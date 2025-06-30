package io.dreamconnected.coa.lxcmanager.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.Executors
import io.dreamconnected.coa.lxcmanager.R
import java.util.concurrent.ExecutorService
import kotlin.system.exitProcess

class ShellClient private constructor() {
    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputReader: BufferedReader? = null

    @Throws(IOException::class)
    fun invokeShell(context:Context) {
        try {
            val processBuilderCheck = ProcessBuilder("which", "su")
            val checkProcess = processBuilderCheck.start()
            val reader = BufferedReader(InputStreamReader(checkProcess.inputStream))
            val suPath = reader.readLine()
            val screenMask = ScreenMask(context)

            if (suPath.isNullOrEmpty()) {
                Log.e("RootCheck", "su command not found")
                screenMask.showDebugDialog(
                    context,
                    context.resources.getString(R.string.root_grant_req),
                    context.resources.getString(R.string.root_grant_err_no_su),
                    onConfirm = {
                        exitProcess(0)
                    })
                return
            }

            val processBuilder = ProcessBuilder("su", "-m")
            processBuilder.redirectErrorStream(true)
            val sharedPref = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE) ?: return
            val defaultLxcPath = context.resources.getString(R.string.lxc_path_default)
            val lxcPath = sharedPref.getString(context.getString(R.string.lxc_path), defaultLxcPath)
            val lxcLdPath = sharedPref.getString(context.getString(R.string.lxc_ld_path), defaultLxcPath)
            val lxcBinPath = sharedPref.getString(context.getString(R.string.lxc_bin_path), defaultLxcPath)
            processBuilder.environment()["HOME"] = lxcPath
            processBuilder.environment()["PATH"] = "$lxcBinPath" + System.getenv("PATH")
            processBuilder.environment()["LD_LIBRARY_PATH"] = lxcLdPath
            process = processBuilder.start()
            outputStream = process?.outputStream
            inputReader = BufferedReader(InputStreamReader(process?.inputStream))
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ShellError", "Error invoking shell process")
        }
    }

    interface CommandOutputListener {
        fun onOutput(output: String?)
        fun onCommandComplete(code: String?)
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    fun execCommand(command: String?, interval: Long, listener: CommandOutputListener?) {
        executor.execute {
            try {
                if (process == null) {
                    Log.d(TAG, "Shell process is not started yet.")
                    return@execute
                }
                Log.d(TAG, "Shell process is available.")

                outputStream!!.write(("sh -c '$command; echo EXITCODE $?'\n").toByteArray())
                outputStream!!.flush()

                var line: String?
                val outputBuilder = StringBuilder()

                while (true) {
                    if (inputReader!!.ready()) {
                        line = inputReader!!.readLine()
                        if (line != null) {
                            Log.d(TAG, "Line: $line")
                            if (line.matches(Regex("EXITCODE \\d+"))) {
                                listener?.let {
                                    Handler(Looper.getMainLooper()).post {
                                        it.onCommandComplete(line)
                                    }
                                }
                                break
                            } else {
                                outputBuilder.append(line).append("\n")
                                listener?.let {
                                    val finalLine: String = line
                                    Handler(Looper.getMainLooper()).post {
                                        it.onOutput(finalLine)
                                    }
                                }
                            }
                        }
                        Thread.sleep(interval)
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun closeShell() {
        if (process != null) {
            process!!.destroy()
            try {
                if (inputReader != null) {
                    inputReader!!.close()
                }
                if (outputStream != null) {
                    outputStream!!.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        internal const val TAG = "ShellClient"
        var instance: ShellClient? = null
            get() {
                if (field == null) {
                    field = ShellClient()
                }
                return field
            }
            private set
    }
}