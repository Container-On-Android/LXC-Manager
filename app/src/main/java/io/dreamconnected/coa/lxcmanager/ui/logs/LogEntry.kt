package io.dreamconnected.coa.lxcmanager.ui.logs

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)
