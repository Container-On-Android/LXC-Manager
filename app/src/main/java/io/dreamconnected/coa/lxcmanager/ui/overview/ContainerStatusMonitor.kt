package io.dreamconnected.coa.lxcmanager.ui.overview

import android.annotation.SuppressLint
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import io.github.coap.lxc.LxcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round

data class ContainerStatus(
    val status: String,
    val cpu: Float = 0f,
    val mem: Float = 0f
)

class ContainerStatusMonitor(
    private val lxcManager: LxcManager?,
    private val containerName: String
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val _statusFlow = MutableStateFlow(ContainerStatus("STOPPED"))
    val statusFlow: StateFlow<ContainerStatus> = _statusFlow.asStateFlow()

    private var monitorJob: Job? = null
    private var previousCpuUse = -1f

    fun startMonitoring(intervalMillis: Long = 1000L) {
        stopMonitoring()
        monitorJob = launch {
            while (isActive) {
                val status = getContainerStatus()
                if (status == "RUNNING") {
                    val (cpu, mem) = getContainerResources()
                    _statusFlow.value = ContainerStatus(status, cpu, mem)
                } else {
                    _statusFlow.value = ContainerStatus(status)
                }
                delay(intervalMillis)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun getContainerStatus(): String {
        return lxcManager?.getContainer(containerName)?.state ?: "STOPPED"
    }

    @SuppressLint("DefaultLocale")
    private suspend fun getContainerResources(): Pair<Float, Float> {
        return withContext(Dispatchers.IO) {
            val result = ShellCommandExecutor.execCommandSync(
                "lxc-info $containerName -H | awk \"/CPU use/ {cpu=\\$3} /Memory use/ {memory=\\$3} END {print cpu \",\" memory}\""
            )
            
            if (result.isNotEmpty()) {
                val parts = result.trim().split(" ")
                if (parts.size >= 2) {
                    val cpuRaw = parts[0].toLongOrNull()?.toDouble()?.div(1000)?.div(1000)?.div(1000)?.toFloat()
                        ?.let { String.format("%.2f", it).toFloat() } ?: 0f
                    val cpu = calculateCpuUsage(cpuRaw, 5)
                    val mem = (parts[1].toFloatOrNull()?.div(1024)?.div(1024)?.div(1024)
                        ?.div(getTotalMemoryInGB())?.times(100))?.toFloat()
                        ?.let { String.format("%.2f", it).toFloat() } ?: 0f
                    return@withContext Pair(cpu, mem)
                }
            }
            Pair(0f, 0f)
        }
    }

    private fun calculateCpuUsage(currentCpuUse: Float, samplingInterval: Long): Float {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        if (previousCpuUse == -1f) {
            previousCpuUse = currentCpuUse
            return 0f
        }
        val increment = currentCpuUse - previousCpuUse
        previousCpuUse = currentCpuUse
        if (increment < 0) return 0f
        val cpuUsage = increment / (samplingInterval * cpuCores)
        return round(cpuUsage * 10000) / 100
    }

    private fun getTotalMemoryInGB(): Double {
        return try {
            val reader = java.io.RandomAccessFile("/proc/meminfo", "r")
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