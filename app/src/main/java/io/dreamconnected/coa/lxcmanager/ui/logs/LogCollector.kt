package io.dreamconnected.coa.lxcmanager.ui.logs

object LogCollector {

    private val appLogs = mutableListOf<LogEntry>()
    private val jniLogs = mutableListOf<LogEntry>()

    fun addAppLog(level: LogLevel, tag: String, message: String) {
        synchronized(appLogs) {
            appLogs.add(LogEntry(System.currentTimeMillis(), level, tag, message))
        }
    }

    fun addJniLog(level: LogLevel, message: String) {
        synchronized(jniLogs) {
            jniLogs.add(LogEntry(System.currentTimeMillis(), level, "lxc", message))
        }
    }

    fun getAppLogs(): List<LogEntry> {
        synchronized(appLogs) {
            return appLogs.toList()
        }
    }

    fun getJniLogs(): List<LogEntry> {
        synchronized(jniLogs) {
            return jniLogs.filter { it.tag == "lxc" }
        }
    }

    fun clearAppLogs() {
        synchronized(appLogs) {
            appLogs.clear()
        }
    }

    fun clearJniLogs() {
        synchronized(jniLogs) {
            jniLogs.clear()
        }
    }
}
