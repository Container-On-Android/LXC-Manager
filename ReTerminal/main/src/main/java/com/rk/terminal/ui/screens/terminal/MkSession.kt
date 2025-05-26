package com.rk.terminal.ui.screens.terminal

import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.pendingCommand
import com.rk.terminal.BuildConfig
import com.rk.terminal.ui.activities.terminal.TerminalActivity
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

object MkSession {
    fun createSession(
        activity: TerminalActivity, sessionClient: TerminalSessionClient, session_id: String, workingMode:Int
    ): TerminalSession {
        with(activity) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val workingDir = pendingCommand?.workingDir ?: "/sdcard"

            val initFile: File = localBinDir().child("init")
            val rish: File = localBinDir().child("rish")
            val lxc: File = localBinDir().child("lxc")

            if (initFile.exists().not()){
                initFile.createFileIfNot()
                initFile.writeText(assets.open("init.sh").bufferedReader().use { it.readText() })
            }

            if (rish.exists().not()){
                rish.createFileIfNot()
                rish.writeText(assets.open("rish.sh").bufferedReader().use { it.readText() })
            }

            if (lxc.exists().not()){
                lxc.createFileIfNot()
                lxc.writeText(assets.open("lxc.sh").bufferedReader().use { it.readText() })
            }

            val env = mutableListOf(
                "PATH=${System.getenv("PATH")}:/sbin:${localBinDir().absolutePath}",
                "HOME=/sdcard",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=${localBinDir()}",
                "EXEC=sh ${localBinDir().child("exec")}",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}"
            )


            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val args: Array<String>

            val shell = if (pendingCommand == null) {
                args = arrayOf("-c",initFile.absolutePath, workingMode.toString(),session_id)
                "/system/bin/sh"
            } else{
                //args = pendingCommand!!.args
                args = arrayOf("-c",lxc.absolutePath, "2",session_id)
                pendingCommand!!.shell
            }

            pendingCommand = null
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }

    }
}