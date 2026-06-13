package io.dreamconnected.coa.lxcmanager.ui.logs

enum class LogLevel(val priority: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    FATAL(5);

    companion object {
        /** Map preference string to a logcat priority letter. */
        fun toLogcatLetter(level: LogLevel): String = when (level) {
            VERBOSE -> "V"
            DEBUG -> "D"
            INFO -> "I"
            WARN -> "W"
            ERROR -> "E"
            FATAL -> "F"
        }

        fun fromPreference(value: String?): LogLevel = when (value?.lowercase()) {
            "verbose", "v" -> VERBOSE
            "debug", "d" -> DEBUG
            "info", "i" -> INFO
            "warn", "warning", "w" -> WARN
            "error", "e" -> ERROR
            "fatal", "f" -> FATAL
            else -> INFO
        }
    }
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)
