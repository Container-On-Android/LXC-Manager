package io.dreamconnected.coa.lxcmanager.ui.overview

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import io.github.coap.lxc.LxcManager
import io.github.coap.lxc.LxcMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Passive monitor for one LXC container.
 *
 * Two independent flows are exposed:
 *  - [stateFlow]   — state transitions are pushed by lxc-monitord via
 *                    the abstract Unix domain socket; the event already
 *                    carries the container name and the new state, so no
 *                    extra query is needed.
 *  - [resourceFlow] — polls CPU / memory only while
 *                    [startMonitoring] was given a positive
 *                    `resourceIntervalMillis` and the container is RUNNING.
 *                    Drives the chart.
 */
class ContainerStatusMonitor(
    private val lxcManager: LxcManager?,
    private val containerName: String
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val tag = "ContainerStatusMonitor_$containerName"

    private val _stateFlow = MutableStateFlow("STOPPED")
    val stateFlow: StateFlow<String> = _stateFlow.asStateFlow()

    private val _resourceFlow = MutableSharedFlow<Pair<Float, Float>>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val resourceFlow: SharedFlow<Pair<Float, Float>> = _resourceFlow.asSharedFlow()

    private var monitorJob: Job? = null
    private var resourceJob: Job? = null
    private var lxcMonitor: LxcMonitor? = null

    // Previous cgroup values for delta calculation
    private var prevCpuNanos: Long = -1
    private var prevMemBytes: Long = -1
    private var prevSampleTime: Long = -1

    /**
     * Start the passive LXC monitor. State transitions are pushed by
     * lxc-monitord and always feed [stateFlow]. If
     * [resourceIntervalMillis] > 0, CPU / memory polling is also started
     * whenever the container is RUNNING and is suspended otherwise.
     *
     * The monitor subscription is kept alive across reconnects: if
     * lxc-monitord has not been spawned yet, has exited because the last
     * client disconnected, or the read fails, we sleep briefly and try
     * again. The state itself is always event-driven — only the
     * subscription is retried.
     */
    fun startMonitoring(resourceIntervalMillis: Long) {
        Log.d(tag, "startMonitoring: resourceInterval=$resourceIntervalMillis")
        stopMonitoring()

        monitorJob = launch {
            while (isActive) {
                // Spawn lxc-monitord for our lxcpath if it isn't running
                // yet. lxc_monitor_open connects to monitord's abstract
                // Unix socket, so it would otherwise fail on a clean boot
                // until the user runs lxc-monitor CLI by hand.
                lxcManager?.ensureMonitord()

                val monitor = lxcManager?.openMonitor()
                if (monitor == null) {
                    Log.w(tag, "lxc_monitor_open returned null; retrying in 1s")
                    delay(1000)
                    continue
                }
                lxcMonitor = monitor
                Log.d(tag, "LXC monitor opened")

                // One-shot snapshot so the switch bar reflects the real
                // state before monitord pushes its first event. This is a
                // single read, not polling — ongoing state changes come
                // from lxc-monitord.
                val initial = lxcManager.getContainer(containerName)?.state ?: "STOPPED"
                publishState(initial, resourceIntervalMillis)
                Log.d(tag, "Initial container status: $initial")

                // Block on lxc_monitor_read; events arrive without polling.
                var needsReconnect = false
                while (isActive && !needsReconnect) {
                    val event = try {
                        monitor.readNext()
                    } catch (t: Throwable) {
                        Log.e(tag, "Monitor read threw: $t")
                        null
                    }
                    if (event == null) {
                        needsReconnect = true
                    } else {
                        Log.d(tag, "Monitor event: $event")
                        if (event.type == LxcMonitor.TYPE_STATE
                            && event.name == containerName) {
                            // The FIFO may carry stale events left over
                            // from a previous container lifecycle, and
                            // the value reported by lxc-monitord is not
                            // always the current state. Use the push
                            // purely as a "something changed" signal and
                            // read the real state through the regular
                            // LXC API. This is a single-shot read, not
                            // polling.
                            val current = lxcManager.getContainer(containerName)?.state
                                ?: event.state
                            publishState(current, resourceIntervalMillis)
                        }
                    }
                }

                monitor.close()
                lxcMonitor = null
                if (isActive) {
                    Log.w(tag, "Monitor disconnected; reconnecting in 1s")
                    delay(1000)
                }
            }
            Log.d(tag, "Monitor loop exited")
        }
    }

    fun stopMonitoring() {
        stopResourcePolling()
        monitorJob?.cancel()
        monitorJob = null
        lxcMonitor?.close()
        lxcMonitor = null
        prevCpuNanos = -1
        prevMemBytes = -1
        prevSampleTime = -1
    }

    private fun publishState(state: String, resourceIntervalMillis: Long) {
        if (_stateFlow.value != state) {
            Log.d(tag, "State changed: ${_stateFlow.value} -> $state")
            _stateFlow.value = state
        }
        if (state == "RUNNING" && resourceIntervalMillis > 0) {
            startResourcePolling(resourceIntervalMillis)
        } else {
            stopResourcePolling()
        }
    }

    private fun startResourcePolling(intervalMillis: Long) {
        stopResourcePolling()
        if (intervalMillis <= 0) return
        resourceJob = launch {
            // First call initializes delta state (returns 0), second call gives real delta
            getContainerResources()
            delay(500)
            while (isActive) {
                if (_stateFlow.value == "RUNNING") {
                    val (cpu, mem) = getContainerResources()
                    Log.d(tag, "Resources - CPU: ${cpu}%, Mem: ${mem}%")
                    _resourceFlow.emit(Pair(cpu, mem))
                }
                delay(intervalMillis)
            }
        }
    }

    private fun stopResourcePolling() {
        resourceJob?.cancel()
        resourceJob = null
    }

    private suspend fun getContainerResources(): Pair<Float, Float> {
        return withContext(Dispatchers.IO) {
            val c = lxcManager?.getContainer(containerName)
            Log.d(tag, "getContainerResources: container = $c")

            val cpuStr = c?.getCgroupItem("cpuacct.usage")
            Log.d(tag, "getContainerResources: cpuStr = '$cpuStr'")

            val memUserStr = c?.getCgroupItem("memory.usage_in_bytes") ?: "0"
            val memKernelStr = c?.getCgroupItem("memory.kmem.usage_in_bytes") ?: "0"

            val cpuNanos = cpuStr?.trim()?.toLongOrNull() ?: -1L
            val memBytes = (memUserStr.trim().toLongOrNull() ?: 0L) + (memKernelStr.trim().toLongOrNull() ?: 0L)

            Log.d(tag, "cgroup raw: cpu=$cpuNanos, mem=$memBytes")

            if (prevCpuNanos < 0 || prevSampleTime < 0) {
                // First sample, initialize and return 0
                prevCpuNanos = cpuNanos
                prevMemBytes = memBytes
                prevSampleTime = System.currentTimeMillis()
                return@withContext Pair(0f, 0f)
            }

            val now = System.currentTimeMillis()
            val deltaTimeMillis = now - prevSampleTime

            // CPU: delta_ns / (delta_time_ms * 1_000_000) / cores * 100
            val cpuDelta = if (cpuNanos > prevCpuNanos) cpuNanos - prevCpuNanos else 0L
            val cpuPercent = if (deltaTimeMillis > 0 && cpuNanos != -1L) {
                val cpuCores = Runtime.getRuntime().availableProcessors().toFloat()
                val cpuUsage = (cpuDelta.toFloat() / (deltaTimeMillis * 1_000_000f)) * 100f / cpuCores
                cpuUsage.coerceIn(0f, 100f)
            } else 0f

            // Memory: container_mem / total_mem * 100
            val memPercent = if (memBytes > 0) {
                val totalMemGB = getTotalMemoryInGB()
                if (totalMemGB > 0) {
                    val memGB = memBytes / (1024.0 * 1024 * 1024)
                    ((memGB / totalMemGB) * 100).coerceIn(0.0, 100.0).toFloat()
                } else 0f
            } else 0f

            prevCpuNanos = cpuNanos
            prevMemBytes = memBytes
            prevSampleTime = now

            Log.d(tag, "calculated: cpu=${cpuPercent}%, mem=${memPercent}%")
            return@withContext Pair(cpuPercent, memPercent)
        }
    }

    private fun getTotalMemoryInGB(): Double {
        val activityManager = ActivityManager::class.java.getMethod("getSystemService", String::class.java)
            .invoke(null, Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        return if (memInfo.totalMem > 0) memInfo.totalMem / (1024.0 * 1024 * 1024) else -1.0
    }
}
