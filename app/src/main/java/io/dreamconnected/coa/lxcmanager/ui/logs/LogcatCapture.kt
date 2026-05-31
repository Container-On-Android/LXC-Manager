package io.dreamconnected.coa.lxcmanager.ui.logs

import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class LogcatCapture(private val tagFilter: String) {
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    companion object {
        private const val TAG = "LogcatCapture"
        private const val MAX_LOGS = 500

        private val PATTERN_ANDROID = Pattern.compile(
            """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+-\d+\s+(\S+)\s+\S+\s+([IWEDV])\s+(.*)$"""
        )

        private val PATTERN_FULL = Pattern.compile(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([IWEDV])\s+(\S+):\s*(.*)$"""
        )

        private val PATTERN_TAG = Pattern.compile(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([IWEDV])\s+(\S+):\s*(.*)$"""
        )

        private val PATTERN_SIMPLE = Pattern.compile(
            """^([IWEDV])\s+(\S+):\s*(.*)$"""
        )

        private val PATTERN_SEPARATOR = Pattern.compile("""^-+$""")
    }

    fun captureAllLogs(callback: (List<LogEntry>) -> Unit) {
        Thread {
            val logs = mutableListOf<LogEntry>()

            try {
                val command = if (tagFilter == "*") {
                    "logcat -d -v brief"
                } else {
                    "logcat -d -v brief -s $tagFilter"
                }

                val process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null && logs.size < MAX_LOGS) {
                    line?.let { logLine ->
                        val trimmedLine = logLine.trim()
                        if (trimmedLine.isNotBlank() && !PATTERN_SEPARATOR.matcher(trimmedLine).matches()) {
                            val logEntry = parseLogLine(trimmedLine)
                            if (logEntry != null && logEntry.tag != TAG) {
                                logs.add(logEntry)
                            }
                        }
                    }
                }

                reader.close()
                process.waitFor()

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(logs)
                }

            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(emptyList())
                }
            }
        }.start()
    }

    fun captureLogsSince(startTime: Long, callback: (List<LogEntry>) -> Unit) {
        Thread {
            val logs = mutableListOf<LogEntry>()

            try {
                val startTimeStr = formatLogcatTime(startTime)
                val command = if (tagFilter == "*") {
                    "logcat -d -v brief -t \"$startTimeStr\""
                } else {
                    "logcat -d -v brief -t \"$startTimeStr\" -s $tagFilter"
                }

                val process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        val trimmedLine = logLine.trim()
                        if (trimmedLine.isNotBlank() && !PATTERN_SEPARATOR.matcher(trimmedLine).matches()) {
                            val logEntry = parseLogLine(trimmedLine)
                            if (logEntry != null && logEntry.tag != TAG) {
                                logs.add(logEntry)
                            }
                        }
                    }
                }

                reader.close()
                process.waitFor()

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(logs)
                }

            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(emptyList())
                }
            }
        }.start()
    }

    private fun parseLogLine(line: String): LogEntry? {
        return try {
            var matcher = PATTERN_ANDROID.matcher(line)
            if (matcher.matches()) {
                val timestamp = matcher.group(1)
                val tag = matcher.group(2)
                val levelStr = matcher.group(3)
                val message = matcher.group(4)
                return LogEntry(
                    parseTimestampAndroid(timestamp),
                    parseLevel(levelStr),
                    tag,
                    message?.trim() ?: ""
                )
            }

            matcher = PATTERN_FULL.matcher(line)
            if (matcher.matches()) {
                val timestamp = matcher.group(1)
                val levelStr = matcher.group(4)
                val tag = matcher.group(5)
                val message = matcher.group(6)
                return LogEntry(
                    parseTimestamp(timestamp),
                    parseLevel(levelStr),
                    tag,
                    message?.trim() ?: ""
                )
            }

            matcher = PATTERN_TAG.matcher(line)
            if (matcher.matches()) {
                val timestamp = matcher.group(1)
                val levelStr = matcher.group(2)
                val tag = matcher.group(3)
                val message = matcher.group(4)
                return LogEntry(
                    parseTimestamp(timestamp),
                    parseLevel(levelStr),
                    tag,
                    message?.trim() ?: ""
                )
            }

            matcher = PATTERN_SIMPLE.matcher(line)
            if (matcher.matches()) {
                val levelStr = matcher.group(1)
                val tag = matcher.group(2)
                val message = matcher.group(3)
                return LogEntry(
                    System.currentTimeMillis(),
                    parseLevel(levelStr),
                    tag,
                    message?.trim() ?: ""
                )
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLevel(levelStr: String?): LogLevel {
        val firstChar = levelStr?.uppercase()?.firstOrNull() ?: return LogLevel.INFO
        return when (firstChar) {
            'I' -> LogLevel.INFO
            'W' -> LogLevel.WARN
            'E' -> LogLevel.ERROR
            'D' -> LogLevel.INFO
            'V' -> LogLevel.INFO
            else -> LogLevel.INFO
        }
    }

    private fun parseTimestamp(timestamp: String?): Long {
        return try {
            timestamp?.let { dateFormat.parse(it)?.time } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseTimestampAndroid(timestamp: String?): Long {
        return try {
            timestamp?.let {
                val androidFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                androidFormat.parse(it)?.time
            } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatLogcatTime(timeMillis: Long): String {
        return dateFormat.format(Date(timeMillis))
    }

    fun shutdown() {
    }
}
