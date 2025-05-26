package com.rk.terminal.ui.screens.terminal

import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.application
import com.rk.libcommons.child
import java.io.File

object Rootfs {
    val reTerminal = File("/sdcard/Download/ReTerminal")

    init {
        if (reTerminal.exists().not()){
            reTerminal.mkdirs()
        }
    }

    var isDownloaded = mutableStateOf(isFilesDownloaded())
    fun isFilesDownloaded(): Boolean{
        return reTerminal.exists() && reTerminal.child("proot").exists() && reTerminal.child("libtalloc.so.2").exists() && reTerminal.child("alpine.tar.gz").exists()
    }
}