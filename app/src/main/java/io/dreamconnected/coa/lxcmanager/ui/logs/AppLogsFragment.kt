package io.dreamconnected.coa.lxcmanager.ui.logs

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.dreamconnected.coa.lxcmanager.R
import java.io.BufferedReader
import java.io.InputStreamReader

class AppLogsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val adapter = LogsAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private var isUserScrolling = false
    private var pendingRefresh = false
    private val logPattern by lazy { Regex("""^([IWEDVFA])\s*/\s*(\S+?)\s*\(\d+\)\s*:\s*(.*)$""") }
    private val MAX_LOGS = 500

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) return

            if (isUserScrolling) {
                pendingRefresh = true
                handler.postDelayed(this, 3000)
            } else {
                pendingRefresh = false
                refreshLogs()
                handler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_log_list, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        startLogCapture()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                isUserScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val itemCount = adapter.itemCount

                if (!isUserScrolling && lastVisibleItem >= itemCount - 1 && pendingRefresh) {
                    pendingRefresh = false
                    refreshLogs()
                }
            }
        })
    }

    private fun startLogCapture() {
        isUserScrolling = false
        pendingRefresh = false
        refreshLogs()
        handler.post(refreshRunnable)
    }

    private fun refreshLogs() {
        if (!isAdded || isUserScrolling) return

        val minLevel = readMinLogLevel()

        Thread {
            try {
                val process = Runtime.getRuntime().exec("logcat -d -v brief -t 500")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val newLogs = mutableListOf<LogEntry>()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        val trimmed = logLine.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("-") && !trimmed.contains("beginning of")) {
                            val entry = parseLogLine(trimmed)
                            if (entry != null && entry.level.priority >= minLevel.priority) {
                                newLogs.add(entry)
                            }
                        }
                    }
                }

                reader.close()
                process.waitFor()

                val limitedLogs = newLogs.takeLast(MAX_LOGS)

                activity?.runOnUiThread {
                    if (!isUserScrolling) {
                        displayLogs(limitedLogs)
                    }
                }

            } catch (_: Exception) {
            }
        }.start()
    }

    private fun readMinLogLevel(): LogLevel {
        val context = context ?: return LogLevel.INFO
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return LogLevel.fromPreference(prefs.getString("log_level", "info"))
    }

    private fun parseLogLine(line: String): LogEntry? {
        return try {
            val match = logPattern.find(line) ?: return null

            val levelStr = match.groupValues[1]
            val tag = match.groupValues[2]
            val message = match.groupValues[3]

            val level = when (levelStr) {
                "V" -> LogLevel.VERBOSE
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E" -> LogLevel.ERROR
                "F", "A" -> LogLevel.FATAL
                else -> LogLevel.INFO
            }

            LogEntry(System.currentTimeMillis(), level, tag, message)
        } catch (_: Exception) {
            null
        }
    }

    private fun displayLogs(newLogs: List<LogEntry>) {
        if (!isAdded) return

        adapter.submitList(newLogs) {
            if (!isUserScrolling && newLogs.isNotEmpty()) {
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        if (!isAdded || adapter.itemCount == 0) return
        recyclerView.post {
            if (!isAdded) return@post
            val count = adapter.itemCount
            if (count <= 0) return@post
            val target = (count - 1).coerceAtLeast(0)
            try {
                recyclerView.smoothScrollToPosition(target)
            } catch (_: IllegalArgumentException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
    }
}
