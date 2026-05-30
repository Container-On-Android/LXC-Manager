package io.dreamconnected.coa.lxcmanager.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.dreamconnected.coa.lxcmanager.R

object ShellCommandExecutor {

    interface CommandOutputListener {
        fun onOutput(output: String?)
        fun onCommandComplete(success: Boolean, exitCode: Int, output: String?)
    }

    private const val TAG = "ShellCommandExecutor"
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
        )
    }

    private fun buildEnvironmentCommands(): List<String> {
        val context = appContext ?: return emptyList()
        val sharedPref = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val defaultLxcPath = context.getString(R.string.lxc_path_default)
        val lxcPath = sharedPref.getString(context.getString(R.string.lxc_path), defaultLxcPath) ?: defaultLxcPath
        val lxcLdPath = sharedPref.getString(context.getString(R.string.lxc_ld_path), defaultLxcPath) ?: defaultLxcPath
        val lxcBinPath = sharedPref.getString(context.getString(R.string.lxc_bin_path), defaultLxcPath) ?: defaultLxcPath
        val systemPath = System.getenv("PATH") ?: ""

        val normalizedBinPath = if (lxcBinPath.endsWith(":")) lxcBinPath else "$lxcBinPath:"
        val normalizedLdPath = if (lxcLdPath.endsWith(":")) lxcLdPath else "$lxcLdPath:"

        return listOf(
            "export HOME=$lxcPath",
            "export PATH=$normalizedBinPath$systemPath",
            "export LD_LIBRARY_PATH=/system/lib64:/system/lib:$normalizedLdPath"
        )
    }

    fun execCommand(command: String, listener: CommandOutputListener?) {
        try {
            val envCommands = buildEnvironmentCommands()
            val finalCommands = if (envCommands.isEmpty()) listOf(command) else envCommands + command
            val outputList = mutableListOf<String>()
            val callbackList = object : CallbackList<String>() {
                override fun onAddElement(s: String?) {
                    outputList.add(s ?: "")
                    listener?.let { mainHandler.post { it.onOutput(s) } }
                }
            }
            Shell.cmd(*finalCommands.toTypedArray())
                .to(callbackList)
                .submit { result ->
                    listener?.let { mainHandler.post {
                        it.onCommandComplete(result.isSuccess, result.code, outputList.joinToString("\n"))
                    } }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $command", e)
            listener?.let { mainHandler.post { it.onCommandComplete(false, -1, e.message) } }
        }
    }

    fun execCommandSync(command: String): String {
        return try {
            val envCommands = buildEnvironmentCommands()
            val finalCommands = if (envCommands.isEmpty()) listOf(command) else envCommands + command
            val result = Shell.cmd(*finalCommands.toTypedArray()).exec()
            result.out.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command synchronously: $command", e)
            ""
        }
    }
}
