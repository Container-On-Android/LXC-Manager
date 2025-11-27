package io.dreamconnected.coa.lxcmanager.ui.overview

import android.annotation.SuppressLint
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round

interface OnSyncListener {
    fun onSync(status: String, cpu: Float, mem: Float)
}

class SyncContainerStatus(
    private val listener: OnSyncListener
) : CoroutineScope {

    private var job: Job? = null
    private var statusCheckJob: Job? = null
    private var controller: SyncController? = null
    private var paused = false
    private var containerName: String = ""
    private var intervalMillis: Long = 1000L
    private var previousCpuUse = -1f
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    fun start(name: String, interval: Long): SyncController {
        stop()
        containerName = name
        intervalMillis = interval
        job = launch {
            while (isActive) {
                if (paused) {
                    delay(intervalMillis)
                    continue
                }
                val (status, cpu, mem) = syncContainerInfo(containerName)
                withContext(Dispatchers.Main) {
                    listener.onSync(status, cpu, mem)
                }
                delay(intervalMillis)
            }
        }
        controller = SyncController(this)
        return controller!!
    }

    class SyncController(private val status: SyncContainerStatus) {
        fun stop() = status.stop()
        fun pause() = status.pause()
        fun resume() = status.resume()
    }

    fun stop() {
        job?.cancel()
        statusCheckJob?.cancel()
        controller = null
        job = null
        statusCheckJob = null
        paused = false
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    suspend fun syncContainerInfo(name: String): Triple<String, Float, Float> =
        suspendCoroutine { cont ->
            var resumed = false
            ShellCommandExecutor.execCommand(
                "lxc-info $name -H | awk \"/State/ {state=\\$2} /CPU use/ {cpu=\\$3} /Memory use/ {memory=\\$3} END {print state \",\" cpu \",\" memory}\"",
                object : ShellCommandExecutor.CommandOutputListener {
                    @SuppressLint("DefaultLocale")
                    override fun onOutput(output: String?) {
                        if (output.isNullOrBlank() || resumed) return
                        val parts = output.trim().split(" ")
                        resumed = true
                        when (parts.size) {
                            3 -> {
                                val status = parts[0]
                                val cpuRaw =
                                    parts[1].toLongOrNull()?.toDouble()?.div(1000)?.div(1000)?.div(1000)?.toFloat()
                                        ?.let { String.format("%.2f", it).toFloat() } ?: 0f
                                val cpu = calculateCpuUsage(cpuRaw, 5)
                                val mem =
                                    (parts[2].toFloatOrNull()?.div(1024)?.div(1024)?.div(1024)
                                        ?.div(getTotalMemoryInGB())?.times(100))
                                        ?.toFloat()?.let { String.format("%.2f", it).toFloat() } ?: 0f
                                cont.resume(Triple(status, cpu, mem))
                            }
                            1 -> {
                                cont.resume(Triple(parts[0], 0f, 0f))
                            }
                            else -> cont.resume(Triple("STOPPED", 0f, 0f))
                        }
                    }
                    override fun onCommandComplete(code: String?) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(Triple("STOPPED", 0f, 0f))
                        }
                    }
                }
            )
        }

    fun startStatusCheck() {
        statusCheckJob?.cancel()
        statusCheckJob = launch {
            while (isActive) {
                if (paused) {
                    val (status, cpu, mem) = syncContainerInfo(containerName)
                    withContext(Dispatchers.Main) {
                        listener.onSync(status, cpu, mem)
                        if (status == "RUNNING") {
                            resume()
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    fun calculateCpuUsage(currentCpuUse: Float, samplingInterval: Long): Float {
        val cpuCores = Runtime.getRuntime().availableProcessors()

        if (previousCpuUse == -1f) {
            previousCpuUse = currentCpuUse
            return 0f
        }

        val increment = currentCpuUse - previousCpuUse

        previousCpuUse = currentCpuUse

        if (increment < 0) { return 0f }

        val cpuUsage = increment / (samplingInterval * cpuCores)

        return round(cpuUsage * 10000) / 100
    }

    fun getTotalMemoryInGB(): Double {
        return try {
            val reader = RandomAccessFile("/proc/meminfo", "r")
            val load = reader.readLine()
            val memInfo = load.replace(Regex("\\D+"), "")
            reader.close()
            val totalMemoryInBytes = memInfo.toLong() * 1024
            totalMemoryInBytes / (1024.0 * 1024 * 1024)
        } catch (e: Exception) {
            e.printStackTrace()
            -1.0
        }
    }
}
