package io.dreamconnected.coa.lxcmanager.service

import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.topjohnwu.superuser.ipc.RootService
import io.dreamconnected.coa.lxcmanager.IDecompressCallback
import io.dreamconnected.coa.lxcmanager.IDecompressService
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

class DecompressService : RootService() {

    private val binder = object : IDecompressService.Stub() {
        override fun extractTarXz(inputPath: String?, outputPath: String?, callback: IDecompressCallback?) {
            if (inputPath.isNullOrEmpty() || outputPath.isNullOrEmpty()) {
                try {
                    callback?.onComplete(false)
                } catch (_: RemoteException) {}
                return
            }
            
            Thread {
                try {
                    extractTarXzInternal(inputPath, outputPath, callback)
                    try {
                        callback?.onComplete(true)
                    } catch (_: RemoteException) {}
                } catch (_: Exception) {
                    try {
                        callback?.onComplete(false)
                    } catch (_: RemoteException) {}
                }
            }.start()
        }
    }

    override fun onBind(p0: Intent): IBinder {
        return binder
    }

    private fun extractTarXzInternal(inputPath: String, outputPath: String, callback: IDecompressCallback?) {
        val inputFile = File(inputPath)
        val outputDir = File(outputPath)
        
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val totalSize = inputFile.length()
        var processedSize: Long = 0

        FileInputStream(inputFile).use { fileIn ->
            XZCompressorInputStream(fileIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextEntry
                    while (entry != null) {
                        val entryFile = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            entry.mode.let { mode ->
                                try {
                                    Files.setPosixFilePermissions(
                                        entryFile.toPath(),
                                        PosixFilePermissions.fromString(intToOctalMode(mode))
                                    )
                                } catch (_: Exception) {}
                            }
                        } else if (entry.isSymbolicLink) {
                            entryFile.parentFile?.mkdirs()
                            val linkPath: Path = entryFile.toPath()
                            val targetPath: Path = Paths.get(entry.linkName)
                            try {
                                Files.deleteIfExists(linkPath)
                                Files.createSymbolicLink(linkPath, targetPath)
                            } catch (_: Exception) {
                                copyTarEntryToFile(tarIn, entryFile)
                            }
                        } else {
                            entryFile.parentFile?.mkdirs()
                            copyTarEntryToFile(tarIn, entryFile)
                            processedSize += entry.size
                            
                            if (totalSize > 0) {
                                val progress = ((processedSize * 100) / totalSize).toInt()
                                try {
                                    callback?.onProgress(progress)
                                } catch (_: RemoteException) {}
                            }

                            entry.mode.let { mode ->
                                try {
                                    Files.setPosixFilePermissions(
                                        entryFile.toPath(),
                                        PosixFilePermissions.fromString(intToOctalMode(mode))
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }

    private fun copyTarEntryToFile(tarIn: TarArchiveInputStream, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            val buffer = ByteArray(8192)
            var len: Int
            while (tarIn.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
            }
        }
    }

    private fun intToOctalMode(mode: Int): String {
        val perms = StringBuilder()
        perms.append(if (mode and 0x100 != 0) 'r' else '-')
        perms.append(if (mode and 0x80 != 0) 'w' else '-')
        perms.append(if (mode and 0x40 != 0) 'x' else '-')
        perms.append(if (mode and 0x20 != 0) 'r' else '-')
        perms.append(if (mode and 0x10 != 0) 'w' else '-')
        perms.append(if (mode and 0x8 != 0) 'x' else '-')
        perms.append(if (mode and 0x4 != 0) 'r' else '-')
        perms.append(if (mode and 0x2 != 0) 'w' else '-')
        perms.append(if (mode and 0x1 != 0) 'x' else '-')
        return perms.toString()
    }
}