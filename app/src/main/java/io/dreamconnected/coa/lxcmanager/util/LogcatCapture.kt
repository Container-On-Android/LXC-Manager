package io.dreamconnected.coa.lxcmanager.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class LogcatCapture(private val tagFilter: String) {
    
    fun captureLogs(startTime: Long, endTime: Long, callback: (List<String>) -> Unit) {
        val logs = mutableListOf<String>()
        
        try {
            // 使用 -t 和 -s 参数：指定时间和 tag
            val startTimeStr = formatLogcatTime(startTime)
            val command = "logcat -d -v brief -t \"$startTimeStr\" -s $tagFilter"
            Log.d("LogcatCapture", "Executing: $command")
            
            ShellCommandExecutor.execCommand(command,
                object : ShellCommandExecutor.CommandOutputListener {
                    override fun onOutput(output: String?) {
                        output?.let { raw ->
                            val lines = raw.split("\n", "\r")
                            
                            lines.forEach { line ->
                                if (line.isNotBlank()) {
                                    logs.add(line)
                                }
                            }
                        }
                    }

                    override fun onCommandComplete(success: Boolean, exitCode: Int, output: String?) {
                        Log.d("LogcatCapture", "Captured ${logs.size} lxc logs")
                        callback(logs)
                    }
                })
        } catch (e: Exception) {
            Log.e("LogcatCapture", "Failed: ${e.message}", e)
            callback(emptyList())
        }
    }
    
    private fun formatLogcatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun shutdown() {}
}