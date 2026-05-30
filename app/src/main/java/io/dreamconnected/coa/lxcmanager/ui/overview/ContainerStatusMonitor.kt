package io.dreamconnected.coa.lxcmanager.ui.overview

import android.annotation.SuppressLint
import android.util.Log
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
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
import java.io.RandomAccessFile
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

    private val TAG = "ContainerStatusMonitor_$containerName"
    private val _statusFlow = MutableSharedFlow<ContainerStatus>(replay = 1)
    val statusFlow: SharedFlow<ContainerStatus> = _statusFlow.asSharedFlow()

    private var monitorJob: Job? = null
    private var previousCpuUse = -1f

    fun startMonitoring(intervalMillis: Long = 1000L) {
        Log.d(TAG, "startMonitoring: interval=$intervalMillis")
        stopMonitoring()
        monitorJob = launch {
            Log.d(TAG, "Monitoring loop started")
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

    @SuppressLint("DefaultLocale")
    private suspend fun getContainerResources(): Pair<Float, Float> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "getContainerResources: Starting resource fetch")
            val result = ShellCommandExecutor.execCommandSync(
                "lxc-info $containerName -H | awk \"/CPU use/ {cpu=\\$3} /Memory use/ {memory=\\$3} END {print cpu \",\" memory}\""
            )
            
            Log.d(TAG, "getContainerResources: lxc-info result: '${result}'")
            
            if (result.isNotEmpty() && !result.contains("inaccessible") && !result.contains("not found") && !result.contains("error")) {
                val parts = result.trim().split(" ")
                Log.d(TAG, "getContainerResources: Parsed parts: $parts")
                
                if (parts.size >= 2) {
                    val cpuRaw = parts[0].toLongOrNull()
                    val memRaw = parts[1].toFloatOrNull()
                    
                    if (cpuRaw != null && memRaw != null) {
                        val cpuFloat = cpuRaw.toDouble().div(1000).div(1000).div(1000).toFloat()
                            .let { String.format("%.2f", it).toFloat() }
                        Log.d(TAG, "getContainerResources: CPU raw value: $cpuFloat")
                        val cpu = calculateCpuUsage(cpuFloat, 1000)
                        val mem = (memRaw.div(1024).div(1024).div(1024)
                            .div(getTotalMemoryInGB()).times(100)).toFloat()
                            .let { String.format("%.2f", it).toFloat() }
                        Log.d(TAG, "getContainerResources: Using lxc-info - CPU: ${cpu}%, Mem: ${mem}%")
                        return@withContext Pair(cpu, mem)
                    } else {
                        Log.d(TAG, "getContainerResources: Failed to parse numeric values from lxc-info")
                    }
                } else {
                    Log.d(TAG, "getContainerResources: Not enough parts from lxc-info")
                }
            }
            
            Log.d(TAG, "getContainerResources: lxc-info failed, falling back to /proc")
            return@withContext getResourcesFromProc()
        }
    }

    private fun getResourcesFromProc(): Pair<Float, Float> {
        val pid = lxcManager?.getContainer(containerName)?.initPid() ?: run {
            Log.d(TAG, "getResourcesFromProc: lxcManager is null or getContainer failed")
            return Pair(0f, 0f)
        }
        Log.d(TAG, "getResourcesFromProc: Container PID: $pid")
        if (pid <= 0) {
            Log.d(TAG, "getResourcesFromProc: Invalid PID: $pid")
            return Pair(0f, 0f)
        }

        val cpu = try {
            val nsInode = ShellCommandExecutor.execCommandSync("readlink /proc/$pid/ns/pid | cut -d'[' -f2 | cut -d']' -f1")
            Log.d(TAG, "getResourcesFromProc: Container PID namespace inode: '$nsInode'")
            
            if (nsInode.isEmpty() || nsInode.isBlank()) {
                Log.d(TAG, "getResourcesFromProc: Failed to get namespace inode")
                return Pair(0f, 0f)
            }
            
            val result = ShellCommandExecutor.execCommandSync(
                "find /proc -maxdepth 1 -type d -name '[0-9]*' 2>/dev/null | while read p; do readlink \"\$p/ns/pid\" 2>/dev/null | grep -q \"$nsInode\" && cat \"\$p/stat\" 2>/dev/null | awk '{print $14 + $15}'; done | awk '{sum += $1} END {print sum}'"
            )
            Log.d(TAG, "getResourcesFromProc: Total jiffies from container processes: '$result'")
            
            if (result.isEmpty() || result.isBlank()) {
                Log.d(TAG, "getResourcesFromProc: Failed to get total jiffies, trying fallback")
                val fallbackResult = ShellCommandExecutor.execCommandSync(
                    "ls /proc/$pid/task/ 2>/dev/null | while read t; do cat /proc/$pid/task/\$t/stat 2>/dev/null | awk '{sum += $14 + $15}'; done | awk '{sum += $1} END {print sum}'"
                )
                Log.d(TAG, "getResourcesFromProc: Fallback jiffies: '$fallbackResult'")
                if (fallbackResult.isEmpty() || fallbackResult.isBlank()) {
                    return Pair(0f, 0f)
                }
                return Pair(calculateCpuUsage(fallbackResult.trim().toLongOrNull()?.toFloat()?.div(100) ?: 0f, 1000), 0f)
            }
            
            val totalJiffies = result.trim().toLongOrNull() ?: 0L
            Log.d(TAG, "getResourcesFromProc: Parsed total jiffies: $totalJiffies")
            
            val totalTime = totalJiffies.toFloat() / 100.0f
            Log.d(TAG, "getResourcesFromProc: totalTime=$totalTime seconds")
            
            val cpuUsage = calculateCpuUsage(totalTime, 1000)
            Log.d(TAG, "getResourcesFromProc: CPU from /proc: ${cpuUsage}%")
            cpuUsage
        } catch (e: Exception) {
            Log.e(TAG, "getResourcesFromProc: Failed to read CPU info", e)
            0f
        }

        val mem = try {
            val statusContent = ShellCommandExecutor.execCommandSync("cat /proc/$pid/status")
            Log.d(TAG, "getResourcesFromProc: status content (first 200 chars): ${statusContent.take(200)}")
            
            if (statusContent.isEmpty() || statusContent.contains("No such file")) {
                Log.d(TAG, "getResourcesFromProc: /proc/$pid/status not accessible")
                return Pair(cpu, 0f)
            }
            
            var memResult: Float
            statusContent.split("\n").forEach { line ->
                if (line.startsWith("VmRSS:")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    Log.d(TAG, "getResourcesFromProc: VmRSS line: $line, parts: $parts")
                    if (parts.size >= 2) {
                        val rssKB = parts[1].toFloatOrNull() ?: 0f
                        Log.d(TAG, "getResourcesFromProc: VmRSS KB: $rssKB")
                        val rssGB = rssKB / 1024f / 1024f
                        val totalGB = getTotalMemoryInGB()
                        Log.d(TAG, "getResourcesFromProc: Total memory GB: $totalGB")
                        if (totalGB > 0) {
                            memResult = (rssGB / totalGB.toFloat() * 100).let { String.format("%.2f", it).toFloat() }
                            Log.d(TAG, "getResourcesFromProc: Mem from /proc: ${memResult}%")
                            return Pair(cpu, memResult)
                        }
                    }
                }
            }
            Log.d(TAG, "getResourcesFromProc: VmRSS not found in status file")
            0f
        } catch (e: Exception) {
            Log.e(TAG, "getResourcesFromProc: Failed to read memory info", e)
            0f
        }

        Log.d(TAG, "getResourcesFromProc: Final result - CPU: ${cpu}%, Mem: ${mem}%")
        return Pair(cpu, mem)
    }

    private fun calculateCpuUsage(currentCpuUse: Float, samplingInterval: Long): Float {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        Log.d(TAG, "calculateCpuUsage: currentCpuUse=$currentCpuUse, previousCpuUse=$previousCpuUse, interval=$samplingInterval, cores=$cpuCores")
        
        if (previousCpuUse == -1f) {
            previousCpuUse = currentCpuUse
            Log.d(TAG, "calculateCpuUsage: First sample, returning 0")
            return 0f
        }
        
        val increment = currentCpuUse - previousCpuUse
        previousCpuUse = currentCpuUse
        
        if (increment < 0) {
            Log.d(TAG, "calculateCpuUsage: Negative increment, returning 0")
            return 0f
        }
        
        val intervalSeconds = samplingInterval / 1000.0f
        val cpuUsage = (increment / intervalSeconds) * 100.0f / cpuCores.toFloat()
        val clamped = if (cpuUsage > 100) 100f else cpuUsage
        val rounded = round(clamped * 100) / 100
        
        Log.d(TAG, "calculateCpuUsage: increment=$increment, intervalSeconds=$intervalSeconds, cpuUsage=$rounded%")
        return rounded
    }

    private fun getTotalMemoryInGB(): Double {
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