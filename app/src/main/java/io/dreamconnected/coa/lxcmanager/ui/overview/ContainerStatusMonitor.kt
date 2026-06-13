package io.dreamconnected.coa.lxcmanager.ui.overview

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import io.github.coap.lxc.LxcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContainerStatus(
    val status: String,
    val cpu: Float = 0f,
    val mem: Float = 0f
)

class ContainerStatusMonitor(
    private val lxcManager: LxcManager?,
    private val containerName: String
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val TAG = "ContainerStatusMonitor_$containerName"
    private val _statusFlow = MutableSharedFlow<ContainerStatus>(replay = 1)
    val statusFlow: SharedFlow<ContainerStatus> = _statusFlow.asSharedFlow()

    private var monitorJob: Job? = null

    // Previous cgroup values for delta calculation
    private var prevCpuNanos: Long = -1
    private var prevMemBytes: Long = -1
    private var prevSampleTime: Long = -1

    fun startMonitoring(intervalMillis: Long) {
        Log.d(TAG, "startMonitoring: interval=$intervalMillis")
        stopMonitoring()
        prevCpuNanos = -1
        prevMemBytes = -1
        prevSampleTime = -1
        monitorJob = launch {
            Log.d(TAG, "Monitoring loop started")

            val initialStatus = getContainerStatus()
            _statusFlow.emit(ContainerStatus(initialStatus))
            Log.d(TAG, "Initial container status: $initialStatus")

            if (initialStatus == "RUNNING") {
                // First call initializes delta state (returns 0), second call gives real delta
                getContainerResources()
                delay(500)
                val (cpu, mem) = getContainerResources()
                _statusFlow.emit(ContainerStatus(initialStatus, cpu, mem))
            }

            while (isActive) {
                val status = getContainerStatus()
                Log.d(TAG, "Container status: $status")
                if (status == "RUNNING") {
                    val (cpu, mem) = getContainerResources()
                    Log.d(TAG, "Resources - CPU: ${cpu}%, Mem: ${mem}%")
                    _statusFlow.emit(ContainerStatus(status, cpu, mem))
                } else {
                    _statusFlow.emit(ContainerStatus(status))
                }
                delay(intervalMillis)
            }
            Log.d(TAG, "Monitoring loop stopped")
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun getContainerStatus(): String {
        return lxcManager?.getContainer(containerName)?.state ?: "STOPPED"
    }

    private suspend fun getContainerResources(): Pair<Float, Float> {
        return withContext(Dispatchers.IO) {
            val c = lxcManager?.getContainer(containerName)
            Log.d(TAG, "getContainerResources: container = $c")
            
            val cpuStr = c?.getCgroupItem("cpuacct.usage")
            Log.d(TAG, "getContainerResources: cpuStr = '$cpuStr'")
            
            val memUserStr = c?.getCgroupItem("memory.usage_in_bytes") ?: "0"
            val memKernelStr = c?.getCgroupItem("memory.kmem.usage_in_bytes") ?: "0"

            val cpuNanos = cpuStr?.trim()?.toLongOrNull() ?: -1L
            val memBytes = (memUserStr.trim().toLongOrNull() ?: 0L) + (memKernelStr.trim().toLongOrNull() ?: 0L)

            Log.d(TAG, "cgroup raw: cpu=$cpuNanos, mem=$memBytes")

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

            Log.d(TAG, "calculated: cpu=${cpuPercent}%, mem=${memPercent}%")
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