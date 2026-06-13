package io.dreamconnected.coa.lxcmanager.ui.download

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.dreamconnected.coa.lxcmanager.R
import io.github.coap.lxc.LxcNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit
import io.dreamconnected.coa.lxcmanager.util.DecompressHelper
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import java.util.Random

class DownloadViewModel : ViewModel() {

    private val TAG = "DownloadViewModel"
    private val PREFS_NAME = "download_prefs"
    private val DOWNLOADS_KEY = "downloads"

    private fun getBaseUrl(context: Context): String {
        val mirror = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("repo_mirror", "images.linuxcontainers.org") ?: "images.linuxcontainers.org"
        return "https://$mirror"
    }
    
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    
    private val _downloadItems = MutableLiveData<List<DownloadItem>>()
    val downloadItems: LiveData<List<DownloadItem>> = _downloadItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun initPrefs(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun addDownload(distribution: String, release: String, architecture: String, variant: String, downloadUrl: String, context: Context) {
        initPrefs(context)
        
        val localPath = getDownloadDirectory(context) + "/${distribution}_${release}_${architecture}_${variant}.tar.xz"
        val id = System.currentTimeMillis()

        val item = DownloadItem(
            id = id,
            distribution = distribution,
            release = release,
            architecture = architecture,
            variant = variant,
            downloadUrl = downloadUrl,
            localPath = localPath,
            status = DownloadStatus.PENDING,
            progress = 0,
            fileSize = 0,
            downloadedSize = 0,
            createdAt = System.currentTimeMillis()
        )

        saveDownloadItem(item)
        startDownload(id, context)
    }

    fun loadDownloads() {
        if (!::prefs.isInitialized) {
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            val downloads = loadDownloadsFromPrefs()
            
            withContext(Dispatchers.Main) {
                _downloadItems.value = downloads
            }
        }
    }

    private fun loadDownloadsFromPrefs(): List<DownloadItem> {
        val json = prefs.getString(DOWNLOADS_KEY, "[]")
        val type = object : TypeToken<List<DownloadItem>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveDownloadItem(item: DownloadItem) {
        val downloads = loadDownloadsFromPrefs().toMutableList()
        val index = downloads.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            downloads[index] = item
        } else {
            downloads.add(item)
        }
        val json = gson.toJson(downloads)
        prefs.edit { putString(DOWNLOADS_KEY, json) }
        notifyObservers()
    }

    private fun removeDownloadItem(id: Long) {
        val downloads = loadDownloadsFromPrefs().toMutableList()
        downloads.removeAll { it.id == id }
        val json = gson.toJson(downloads)
        prefs.edit { putString(DOWNLOADS_KEY, json) }
        notifyObservers()
    }

    private fun notifyObservers() {
        CoroutineScope(Dispatchers.Main).launch {
            _downloadItems.value = loadDownloadsFromPrefs()
        }
    }

    private fun startDownload(id: Long, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val downloads = loadDownloadsFromPrefs()
            val item = downloads.find { it.id == id } ?: return@launch

            try {
                TrafficStats.setThreadStatsTag(0xF00E)
                val fullUrl = if (item.downloadUrl.startsWith("http")) {
                    item.downloadUrl
                } else {
                    "${getBaseUrl(context)}${item.downloadUrl}rootfs.tar.xz"
                }
                val url = URL(fullUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.setRequestProperty("User-Agent", "lxc/1.0 compat:7")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fileSize = connection.contentLength
                    
                    val file = File(item.localPath)
                    file.parentFile?.mkdirs()

                    var input: BufferedInputStream? = null
                    var output: FileOutputStream? = null
                    
                    try {
                        input = BufferedInputStream(connection.inputStream)
                        output = FileOutputStream(file)

                        val data = ByteArray(8192)
                        var totalRead: Long = 0
                        var readCount: Int

                        var updatedItem = item.copy(
                            status = DownloadStatus.DOWNLOADING,
                            fileSize = fileSize.toLong()
                        )
                        saveDownloadItem(updatedItem)

                        while (input.read(data).also { readCount = it } != -1) {
                            totalRead += readCount
                            output.write(data, 0, readCount)

                            val progress = if (fileSize > 0) ((totalRead * 100) / fileSize).toInt() else 0
                            updatedItem = updatedItem.copy(
                                progress = progress,
                                downloadedSize = totalRead
                            )
                            saveDownloadItem(updatedItem)
                        }

                        output.flush()

                        val completedItem = updatedItem.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            downloadedSize = totalRead
                        )
                        saveDownloadItem(completedItem)

                    } finally {
                        output?.close()
                        input?.close()
                    }
                } else {
                    throw Exception("HTTP error code: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for id: $id", e)
                val failedItem = item.copy(status = DownloadStatus.FAILED)
                saveDownloadItem(failedItem)
            }
        }
    }

    fun retryDownload(id: Long, context: Context) {
        initPrefs(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            val downloads = loadDownloadsFromPrefs()
            val item = downloads.find { it.id == id } ?: return@launch
            
            val resetItem = item.copy(status = DownloadStatus.PENDING, progress = 0, downloadedSize = 0)
            saveDownloadItem(resetItem)
            startDownload(id, context)
        }
    }

    fun deleteDownload(item: DownloadItem, context: Context) {
        if (!::prefs.isInitialized) {
            return
        }

        try {
            val file = File(item.localPath)
            if (file.exists()) {
                file.delete()
            }

            val cacheDir = File(
                getDownloadDirectory(context) +
                    "/cache/${item.distribution}/${item.release}/${item.architecture}/${item.variant}"
            )
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete files for download: ${item.id}", e)
        }

        removeDownloadItem(item.id)
    }

    private fun getDownloadDirectory(context: Context): String {
        return context.getExternalFilesDir(null)?.absolutePath + "/lxc_downloads"
    }

    fun installContainer(item: DownloadItem, containerName: String, context: Context, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var containerPath = ""
            var rootfsPath: String
            var cachePath: String

            try {
                val lxcPath = LxcNative.LXC_PATH
                val lxcRootPath = getLxcPath(context)
                containerPath = "$lxcPath/$containerName"
                rootfsPath = "$containerPath/rootfs"
                cachePath = "${getDownloadDirectory(context)}/cache/${item.distribution}/${item.release}/${item.architecture}/${item.variant}"

                File(cachePath).mkdirs()

                val rootfsFile = File(item.localPath)
                val metaUrl = if (item.downloadUrl.startsWith("http")) {
                    item.downloadUrl.replace("rootfs.tar.xz", "meta.tar.xz")
                } else {
                    "${getBaseUrl(context)}${item.downloadUrl}meta.tar.xz"
                }
                val metaFile = File(cachePath, "meta.tar.xz")

                downloadFile(metaUrl, metaFile)

                val createDirsCmd = "mkdir -p $containerPath $rootfsPath"
                val result1 = ShellCommandExecutor.execCommandSync(createDirsCmd)
                if (result1.contains("mkdir:") || result1.contains("error")) {
                    throw Exception("Failed to create directories")
                }

                val rootfsSuccess = DecompressHelper.extractTarXz(context, rootfsFile.absolutePath, rootfsPath)
                if (!rootfsSuccess) {
                    throw Exception("Failed to extract rootfs")
                }

                val metaSuccess = DecompressHelper.extractTarXz(context, metaFile.absolutePath, cachePath)
                if (!metaSuccess) {
                    throw Exception("Failed to extract meta")
                }

                val createDevPtsCmd = "mkdir -p $rootfsPath/dev/pts"
                ShellCommandExecutor.execCommandSync(createDevPtsCmd)

                processTemplateFiles(rootfsPath, cachePath, containerName)

                postExtractSetup(containerPath, rootfsPath)

                generateContainerConfig(item, containerName, containerPath, rootfsPath, cachePath, lxcRootPath)

                withContext(Dispatchers.Main) {
                    callback(true, "Container $containerName created successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install container", e)
                // Clean up on failure
                if (containerPath.isNotEmpty()) {
                    try {
                        val cleanCmd = "rm -rf $containerPath"
                        ShellCommandExecutor.execCommandSync(cleanCmd)
                    } catch (_: Exception) {
                        // Ignore cleanup errors
                    }
                }
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to install container: ${e.message}")
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, destination: File) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "lxc/1.0 compat:7")

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(destination)

            val data = ByteArray(8192)
            var readCount: Int
            while (input.read(data).also { readCount = it } != -1) {
                output.write(data, 0, readCount)
            }

            output.flush()
            output.close()
            input.close()
        } else {
            throw Exception("HTTP error code: ${connection.responseCode}")
        }
        connection.disconnect()
    }

    private fun postExtractSetup(containerPath: String, rootfsPath: String) {
        if (File("$rootfsPath/etc/init/tty.conf").exists()) {
            ShellCommandExecutor.execCommandSync("sed -i 's|mingetty|mingetty --nohangup|' ${rootfsPath}/etc/init/tty.conf")
        }
    }

    private fun processTemplateFiles(rootfsPath: String, cachePath: String, containerName: String) {
        val templatesFile = File(cachePath, "templates")
        if (!templatesFile.exists()) {
            return
        }

        val content = ShellCommandExecutor.execCommandSync("cat '${templatesFile.absolutePath}'")
        if (content.isBlank()) {
            return
        }

        for (rawPath in content.split("\n")) {
            val path = rawPath.trim()
            if (path.isEmpty()) {
                continue
            }
            val target = File("$rootfsPath$path")
            if (!target.exists()) {
                Log.w(TAG, "Template target does not exist: ${target.absolutePath}")
                continue
            }
            val escaped = target.absolutePath.replace("'", "'\\''")
            ShellCommandExecutor.execCommandSync(
                "if [ -f '$escaped' ]; then sed -i 's/LXC_NAME/${containerName.replace("/", "\\/")}/g' '$escaped'; fi"
            )
        }
    }

    private fun generateContainerConfig(item: DownloadItem, containerName: String, containerPath: String, 
                                         rootfsPath: String, cachePath: String, lxcPath: String) {
        val metaConfigFile = File(cachePath, "config")
        val templateConfigPath = "$lxcPath/share/lxc/config"
        val hookDir = "$lxcPath/share/lxc/hooks"
        val configPath = "$containerPath/config"
        val randomMac = generateRandomMacAddress()

        val metaLines = mutableListOf<String>()
        val networkLines = mutableListOf<String>()
        val containerLines = mutableListOf<String>()
        
        if (metaConfigFile.exists()) {
            val rawContent = ShellCommandExecutor.execCommandSync("cat '${metaConfigFile.absolutePath}'")
            val lines = rawContent.split("\n")
            
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("lxc.net.") -> networkLines.add(trimmed)
                    trimmed.startsWith("lxc.") -> containerLines.add(trimmed)
                    trimmed.isNotEmpty() -> metaLines.add(trimmed)
                }
            }
        }

        val sb = StringBuilder()
        
        sb.append("\n\n# Distribution configuration\n")
        for (line in metaLines) {
            val replaced = line
                .replace("LXC_NAME", containerName)
                .replace("LXC_PATH", containerPath)
                .replace("LXC_ROOTFS", rootfsPath)
                .replace("LXC_TEMPLATE_CONFIG", templateConfigPath)
                .replace("LXC_HOOK_DIR", hookDir)
            sb.append(replaced).append("\n")
        }
        
        sb.append("\n\n# Container specific configuration\n")
        sb.append("lxc.rootfs.path = $rootfsPath\n")
        for (line in containerLines) {
            val replaced = line
                .replace("LXC_NAME", containerName)
                .replace("LXC_PATH", containerPath)
                .replace("LXC_ROOTFS", rootfsPath)
                .replace("LXC_TEMPLATE_CONFIG", templateConfigPath)
                .replace("LXC_HOOK_DIR", hookDir)
            sb.append(replaced).append("\n")
        }
        
        val fstabFile = File(cachePath, "fstab")
        if (fstabFile.exists()) {
            sb.append("lxc.mount.fstab = $containerPath/fstab\n")
            ShellCommandExecutor.execCommandSync("cp '${fstabFile.absolutePath}' '$containerPath/fstab' 2>/dev/null || true")
        }
        sb.append("lxc.uts.name = $containerName\n")
        
        sb.append("\n\n# Network configuration\n")
        if (networkLines.isNotEmpty()) {
            for (line in networkLines) {
                sb.append(line).append("\n")
            }
        } else {
            sb.append("lxc.net.0.type = veth\n")
            sb.append("lxc.net.0.link = lxcbr0\n")
            sb.append("lxc.net.0.flags = up\n")
            sb.append("lxc.net.0.hwaddr = $randomMac\n")
        }
        
        val finalContent = sb.toString()
        val escaped = finalContent.replace("'", "'\\''")
        ShellCommandExecutor.execCommandSync("echo '$escaped' > '$configPath'")
    }

    private fun getLxcPath(context: Context): String {
        val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val defaultPath = context.getString(R.string.lxc_path_default)
        return prefs.getString(context.getString(R.string.lxc_path), defaultPath) ?: defaultPath
    }

    private fun generateRandomMacAddress(): String {
        val random = Random()
        val bytes = ByteArray(3)
        random.nextBytes(bytes)
        return String.format("00:16:3e:%02x:%02x:%02x", bytes[0], bytes[1], bytes[2])
    }
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadItem(
    val id: Long,
    val distribution: String,
    val release: String,
    val architecture: String,
    val variant: String,
    val downloadUrl: String,
    val localPath: String,
    val status: DownloadStatus,
    val progress: Int,
    val fileSize: Long,
    val downloadedSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val buildId: String = ""
)