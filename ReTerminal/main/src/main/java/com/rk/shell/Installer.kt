package com.rk.shell

import android.annotation.SuppressLint
import android.os.Environment
import com.rk.libcommons.child
import java.io.File

//Note avoid using android apis as much as possible
class Installer {
    companion object{
        @JvmStatic
        fun main(args: Array<String>){
            val workingDir = "/data/local/tmp/ReTerminal"
            val downloadDir = buildString { Environment.getExternalStorageDirectory().path + "/Download/ReTerminal" }

            if(File(workingDir).exists().not()){
                File(workingDir).mkdirs()
            }

            val busyboxDir = "$workingDir/"

            moveFileIfNeeded("$downloadDir/busybox_arm64", "$workingDir/busybox_arm64")
            //moveFileIfNeeded("$downloadDir/proot", "$workingDir/proot")
            //extractTarIfNeeded(File(downloadDir).child("alpine.tar.gz"), File(alpineDir))
            installBinIfNeeded(File(workingDir).child("busybox_arm64"), File(busyboxDir))
        }

        fun moveFileIfNeeded(source: String, destination: String) {
            val srcFile = File(source)
            val destFile = File(destination)
            if (!destFile.exists() && srcFile.exists()) {
                srcFile.copyTo(destFile, overwrite = true)
                destFile.setExecutable(true)
            }
        }

        fun installBinIfNeeded(binFile: File, destFile: File) {
            if(destFile.exists().not() || destFile.listFiles()!!.isEmpty()){
                if (destFile.exists().not()){
                    destFile.mkdirs()
                }
                executeShell("mkdir -p ${destFile.child("bin").absolutePath}")
                binFile.setExecutable(true)
                executeShell("$binFile --install ${destFile.child("bin").absolutePath}")
            }
        }

        @SuppressLint("NewApi")
        fun executeShell(command: String,useShell: Boolean = true) {
            if (useShell){
                ProcessBuilder("sh", "-c", command).inheritIO().start().waitFor()
            }else{
                ProcessBuilder("command").inheritIO().start().waitFor()
            }
        }
    }
}