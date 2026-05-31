package io.dreamconnected.coa.lxcmanager.ui.logs

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.topjohnwu.superuser.Shell
import io.dreamconnected.coa.lxcmanager.R

class JniLogsFragment : Fragment() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var logContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private var isUserScrolling = false
    private var pendingRefresh = false
    private var shellInitialized = false
    private val logs = mutableListOf<LogEntry>()

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) return

            if (isUserScrolling) {
                pendingRefresh = true
                handler.postDelayed(this, 2000)
            } else if (shellInitialized) {
                pendingRefresh = false
                refreshLogs()
                handler.postDelayed(this, 2000)
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.item_log_entry, container, false)
        scrollView = view.findViewById(R.id.scrollView)
        logContainer = view.findViewById(R.id.logContainer)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupScrollListener()
        initShell()
    }

    private fun initShell() {
        Shell.cmd("echo shell_init").submit { result ->
            shellInitialized = result.isSuccess
            if (shellInitialized) {
                startLogCapture()
            }
        }
    }

    private fun setupScrollListener() {
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (!isAdded) return@addOnScrollChangedListener

            val scrollY = scrollView.scrollY
            val scrollHeight = scrollView.getChildAt(0)?.height ?: 0
            val viewHeight = scrollView.height

            val isAtBottom = scrollY + viewHeight >= scrollHeight - 50
            val isScrolling = !scrollView.canScrollVertically(1)

            if (isAtBottom && isScrolling) {
                if (isUserScrolling) {
                    isUserScrolling = false
                    handler.post {
                        scrollToBottom()
                        if (pendingRefresh && shellInitialized) {
                            refreshLogs()
                            pendingRefresh = false
                        }
                    }
                }
            } else {
                isUserScrolling = true
            }
        }
    }

    private fun startLogCapture() {
        isRefreshing = true
        isUserScrolling = false
        pendingRefresh = false
        refreshLogs()
        handler.post(refreshRunnable)
    }

    private fun refreshLogs() {
        if (!isAdded || isUserScrolling || !shellInitialized) return

        Thread {
            try {
                val newLogs = mutableListOf<LogEntry>()

                Shell.cmd("logcat -d -v brief")
                    .submit { result ->
                        if (result.isSuccess) {
                            val output = result.out.joinToString("\n")
                            val lines = output.split("\n")

                            lines.forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotBlank() && !trimmed.startsWith("-") && !trimmed.contains("beginning of")) {
                                    val entry = parseLogLine(trimmed)
                                    if (entry != null && (entry.tag == "lxc" || entry.tag == "LxcContainer")) {
                                        newLogs.add(entry)
                                    }
                                }
                            }
                        }

                        activity?.runOnUiThread {
                            if (!isUserScrolling) {
                                displayLogs(newLogs)
                            }
                        }
                    }

            } catch (e: Exception) {
            }
        }.start()
    }

    private fun parseLogLine(line: String): LogEntry? {
        return try {
            val pattern = Regex("""^([IWEDV])\s*/\s*(\S+?)\s*\(\d+\)\s*:\s*(.*)$""")
            val match = pattern.find(line) ?: return null

            val levelStr = match.groupValues[1]
            val tag = match.groupValues[2]
            val message = match.groupValues[3]

            val level = when (levelStr) {
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E" -> LogLevel.ERROR
                "D" -> LogLevel.INFO
                "V" -> LogLevel.INFO
                else -> LogLevel.INFO
            }

            LogEntry(System.currentTimeMillis(), level, tag, message)
        } catch (e: Exception) {
            null
        }
    }

    private fun displayLogs(newLogs: List<LogEntry>) {
        if (!isAdded || isUserScrolling) return

        logs.clear()
        logs.addAll(newLogs)

        logContainer.removeAllViews()

        logs.takeLast(100).forEach { logEntry ->
            val logView = createLogView(logEntry)
            logContainer.addView(logView)
        }

        if (!isUserScrolling) {
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        if (!isAdded) return
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createLogView(logEntry: LogEntry): View {
        return TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.log_item_margin)
            }

            val backgroundColor = when (logEntry.level) {
                LogLevel.INFO -> ContextCompat.getColor(context, R.color.log_info_background)
                LogLevel.WARN -> ContextCompat.getColor(context, R.color.log_warn_background)
                LogLevel.ERROR -> ContextCompat.getColor(context, R.color.log_error_background)
            }

            setBackgroundColor(backgroundColor)
            setPadding(16, 12, 16, 12)
            text = "[${logEntry.level}] [${logEntry.tag}] ${logEntry.message}"
            textSize = 11f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isRefreshing = false
        handler.removeCallbacks(refreshRunnable)
    }
}
