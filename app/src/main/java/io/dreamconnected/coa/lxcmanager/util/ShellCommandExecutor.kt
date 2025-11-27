package io.dreamconnected.coa.lxcmanager.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.dreamconnected.coa.lxcmanager.R
import kotlin.system.exitProcess

object ShellCommandExecutor {

    interface CommandOutputListener {
        fun onOutput(output: String?)
        fun onCommandComplete(code: String?)
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
        Shell.isAppGrantedRoot()?.let {
            if (!it) ScreenMask(context).showDebugDialog(
                context,
                context.resources.getString(R.string.root_grant_req),
                context.resources.getString(R.string.root_grant_err_no_su),
                onConfirm = {
                    exitProcess(0)
                })
        }
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
            Shell.cmd(*finalCommands.toTypedArray()).submit { result ->
                val outputs = (result.out ?: emptyList()) + (result.err ?: emptyList())
                outputs.forEach { line ->
                    listener?.let { mainHandler.post { it.onOutput(line) } }
                }
                listener?.let { mainHandler.post { it.onCommandComplete("EXITCODE ${result.code}") } }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $command", e)
            listener?.let { mainHandler.post { it.onCommandComplete("EXITCODE 1") } }
        }
    }
}
