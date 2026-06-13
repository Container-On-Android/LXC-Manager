package io.dreamconnected.coa.lxcmanager.ui.repos

import android.app.Application
import android.content.SharedPreferences
import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class ReposViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ReposViewModel"
    private val baseUrl: String
        get() {
            val mirror = PreferenceManager.getDefaultSharedPreferences(getApplication())
                .getString("repo_mirror", "images.linuxcontainers.org") ?: "images.linuxcontainers.org"
            return "https://$mirror"
        }
    private val indexPath = "/meta/1.0/index-system"
    private val userAgent = "lxc/1.0 compat:7"
    private val threadStatsTag = 0xF00D
    private val cacheExpiryDays = 1L

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("repos_cache", android.content.Context.MODE_PRIVATE)
    }

    private val defaultPrefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(application)
    }

    private val _images = MutableLiveData<List<ImageItem>>()
    val images: LiveData<List<ImageItem>> = _images

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    @Suppress("UNUSED")
    private val _selectedDistribution = MutableLiveData<String>()
    @Suppress("UNUSED")
    val selectedDistribution: LiveData<String> = _selectedDistribution

    @Suppress("UNUSED")
    private val _selectedArchitecture = MutableLiveData<String?>()
    @Suppress("UNUSED")
    val selectedArchitecture: MutableLiveData<String?> = _selectedArchitecture

    private val _distributions = MutableLiveData<List<String>>()
    val distributions: LiveData<List<String>> = _distributions

    private val _architectures = MutableLiveData<List<String>>()
    val architectures: LiveData<List<String>> = _architectures

    @Volatile
    private var memoryCache: Pair<List<String>, List<ImageItem>>? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "repo_mirror") {
            memoryCache = null
            prefs.edit {
                remove("selected_distribution")
                    .remove("selected_architecture")
            }
        }
    }

    init {
        defaultPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private suspend fun loadFromCacheAsync(): Pair<List<String>, List<ImageItem>>? = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }

        if (!isCacheValid()) {
            return@withContext null
        }

        val distributionsJson = prefs.getString("cached_distributions", null) ?: return@withContext null
        val imagesJson = prefs.getString("cached_images", null) ?: return@withContext null

        try {
            val distributionsArray = JSONArray(distributionsJson)
            val distributions = mutableListOf<String>()
            for (i in 0 until distributionsArray.length()) {
                distributions.add(distributionsArray.getString(i))
            }

            val imagesArray = JSONArray(imagesJson)
            val images = mutableListOf<ImageItem>()
            for (i in 0 until imagesArray.length()) {
                val obj = imagesArray.getJSONObject(i)
                images.add(
                    ImageItem(
                        distribution = obj.getString("distribution"),
                        release = obj.getString("release"),
                        architecture = obj.getString("architecture"),
                        variant = obj.getString("variant"),
                        fullPath = obj.getString("fullPath"),
                        downloadUrl = obj.getString("downloadUrl")
                    )
                )
            }

            val result = Pair(distributions, images)
            memoryCache = result
            result
        } catch (e: Exception) {
            Log.e(tag, "Failed to load from cache", e)
            null
        }
    }

    private fun isCacheValid(): Boolean {
        val lastCacheTime = prefs.getLong("last_cache_time", 0L)
        val cacheExpiryMillis = TimeUnit.DAYS.toMillis(cacheExpiryDays)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastCacheTime) < cacheExpiryMillis
    }

    private fun saveCache(distributions: List<String>, images: List<ImageItem>) {
        memoryCache = Pair(distributions, images)
        prefs.edit().apply {
            putLong("last_cache_time", System.currentTimeMillis())

            val distributionsArray = JSONArray()
            distributions.forEach { distributionsArray.put(it) }
            putString("cached_distributions", distributionsArray.toString())

            val imagesArray = JSONArray()
            images.forEach { image ->
                val jsonObject = JSONObject().apply {
                    put("distribution", image.distribution)
                    put("release", image.release)
                    put("architecture", image.architecture)
                    put("variant", image.variant)
                    put("fullPath", image.fullPath)
                    put("downloadUrl", image.downloadUrl)
                }
                imagesArray.put(jsonObject)
            }
            putString("cached_images", imagesArray.toString())

            apply()
        }
    }

    fun loadDistributions() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val cachedData = loadFromCacheAsync()
            if (cachedData != null) {
                withContext(Dispatchers.Main) {
                    _distributions.value = cachedData.first
                    if (cachedData.first.isNotEmpty()) {
                        val savedDist = prefs.getString("selected_distribution", null)
                        val activeDist = if (savedDist != null && cachedData.first.contains(savedDist)) {
                            savedDist
                        } else {
                            cachedData.first[0]
                        }
                        _selectedDistribution.value = activeDist
                        val availableArchs = cachedData.second.filter { it.distribution == activeDist }.map { it.architecture }.distinct().sorted()
                        _architectures.value = availableArchs
                        val savedArch = prefs.getString("selected_architecture", null)
                        val activeArch = when {
                            savedArch != null && availableArchs.contains(savedArch) -> savedArch
                            availableArchs.contains("arm64") -> "arm64"
                            else -> availableArchs.firstOrNull()
                        }
                        _selectedArchitecture.value = activeArch
                        if (activeArch != null) {
                            _images.value = cachedData.second.filter { it.distribution == activeDist && it.architecture == activeArch }
                        } else {
                            _images.value = cachedData.second.filter { it.distribution == activeDist }
                        }
                    } else {
                        _images.value = emptyList()
                    }
                    _isLoading.value = false
                }
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    TrafficStats.setThreadStatsTag(threadStatsTag)
                    val url = URL("$baseUrl$indexPath")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val rawBytes = connection.inputStream.use { it.readBytes() }
                        if (rawBytes.isEmpty() || rawBytes.all { it == 0.toByte() }) {
                            throw Exception("Mirror returned empty data. It may not sync LXC index files. Try images.linuxcontainers.org, mirrors.ustc.edu.cn/lxc-images, or mirror.nju.edu.cn/lxc-images.")
                        }
                        val reader = BufferedReader(InputStreamReader(rawBytes.inputStream()))
                        val distributionSet = mutableSetOf<String>()
                        val allImages = mutableListOf<ImageItem>()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            line?.let {
                                val parts = it.split(";")
                                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                                    distributionSet.add(parts[0])
                                }
                                if (parts.size >= 6 && parts[0].isNotEmpty()) {
                                    val distribution = parts[0]
                                    val release = parts[1]
                                    val arch = parts[2]
                                    val variant = parts[3]
                                    val downloadUrl = parts[5]
                                    val fullPath = "$distribution/$release/$arch/$variant"
                                    allImages.add(
                                        ImageItem(
                                            distribution = distribution,
                                            release = release,
                                            architecture = arch,
                                            variant = variant,
                                            fullPath = fullPath,
                                            downloadUrl = downloadUrl
                                        )
                                    )
                                }
                            }
                        }
                        reader.close()

                        if (distributionSet.isEmpty() && allImages.isEmpty()) {
                            throw Exception("Mirror returned no LXC index data. The mirror may not provide meta/1.0/index-system. Try images.linuxcontainers.org, mirrors.ustc.edu.cn/lxc-images, or mirror.nju.edu.cn/lxc-images.")
                        }

                        val dists = distributionSet.sorted()
                        allImages.sortWith(compareBy({ it.release }, { it.architecture }, { it.variant }))
                        saveCache(dists, allImages)

                        withContext(Dispatchers.Main) {
                            _distributions.value = dists
                            if (dists.isNotEmpty()) {
                                val savedDist = prefs.getString("selected_distribution", null)
                                val activeDist = if (savedDist != null && dists.contains(savedDist)) {
                                    savedDist
                                } else {
                                    dists[0]
                                }
                                _selectedDistribution.value = activeDist
                                val availableArchs = allImages.filter { it.distribution == activeDist }.map { it.architecture }.distinct().sorted()
                                _architectures.value = availableArchs
                                val savedArch = prefs.getString("selected_architecture", null)
                                val activeArch = when {
                                    savedArch != null && availableArchs.contains(savedArch) -> savedArch
                                    availableArchs.contains("arm64") -> "arm64"
                                    else -> availableArchs.firstOrNull()
                                }
                                _selectedArchitecture.value = activeArch
                                if (activeArch != null) {
                                    _images.value = allImages.filter { it.distribution == activeDist && it.architecture == activeArch }
                                } else {
                                    _images.value = allImages.filter { it.distribution == activeDist }
                                }
                            } else {
                                _images.value = emptyList()
                            }
                        }
                    } else {
                        throw Exception("HTTP error code: $responseCode")
                    }
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load distributions", e)
                withContext(Dispatchers.Main) {
                    _error.value = e.message ?: "Failed to load distributions"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun filterByArchitecture(architecture: String) {
        val currentDist = _selectedDistribution.value ?: return

        viewModelScope.launch {
            _selectedArchitecture.value = architecture
            prefs.edit { putString("selected_architecture", architecture) }

            val cachedData = loadFromCacheAsync()
            if (cachedData != null) {
                val filteredImages = cachedData.second.filter {
                    it.distribution == currentDist && it.architecture == architecture
                }
                withContext(Dispatchers.Main) {
                    _images.value = filteredImages
                }
                return@launch
            }

            // If no cache, filter from current images (which were loaded from network)
            val currentImages = _images.value ?: return@launch
            val filteredImages = currentImages.filter { it.architecture == architecture }
            withContext(Dispatchers.Main) {
                _images.value = filteredImages
            }
        }
    }

    fun loadImages(distribution: String, architecture: String? = null) {
        prefs.edit { putString("selected_distribution", distribution) }
        if (architecture != null) {
            prefs.edit { putString("selected_architecture", architecture) }
        }

        viewModelScope.launch {
            val cachedData = loadFromCacheAsync()
            if (cachedData != null) {
                val filteredImages = cachedData.second.filter { it.distribution == distribution }
                val availableArchs = filteredImages.map { it.architecture }.distinct().sorted()
                val targetArch = architecture ?: when {
                    availableArchs.contains("arm64") -> "arm64"
                    else -> availableArchs.firstOrNull()
                }
                withContext(Dispatchers.Main) {
                    _selectedDistribution.value = distribution
                    _architectures.value = availableArchs
                    _selectedArchitecture.value = targetArch
                    if (targetArch != null) {
                        _images.value = filteredImages.filter { it.architecture == targetArch }
                    } else {
                        _images.value = filteredImages
                    }
                }
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                withContext(Dispatchers.IO) {
                    TrafficStats.setThreadStatsTag(threadStatsTag)
                    val url = URL("$baseUrl$indexPath")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val rawBytes = connection.inputStream.use { it.readBytes() }
                        val reader = BufferedReader(InputStreamReader(rawBytes.inputStream()))
                        val imageList = mutableListOf<ImageItem>()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            line?.let {
                                val parts = it.split(";")
                                if (parts.size >= 6 && parts[0] == distribution) {
                                    val release = parts[1]
                                    val arch = parts[2]
                                    val variant = parts[3]
                                    val downloadUrl = parts[5]
                                    val fullPath = "$distribution/$release/$arch/$variant"
                                    imageList.add(
                                        ImageItem(
                                            distribution = distribution,
                                            release = release,
                                            architecture = arch,
                                            variant = variant,
                                            fullPath = fullPath,
                                            downloadUrl = downloadUrl
                                        )
                                    )
                                }
                            }
                        }
                        reader.close()

                        val availableArchs = imageList.map { it.architecture }.distinct().sorted()
                        val targetArch = architecture ?: when {
                            availableArchs.contains("arm64") -> "arm64"
                            else -> availableArchs.firstOrNull()
                        }
                        val sortedList = if (targetArch != null) {
                            imageList.filter { it.architecture == targetArch }
                                .sortedWith(compareBy({ it.release }, { it.variant }))
                        } else {
                            imageList.sortedWith(compareBy({ it.release }, { it.architecture }, { it.variant }))
                        }
                        withContext(Dispatchers.Main) {
                            _selectedDistribution.value = distribution
                            _architectures.value = availableArchs
                            _selectedArchitecture.value = targetArch
                            _images.value = sortedList
                        }
                    } else {
                        throw Exception("HTTP error code: $responseCode")
                    }
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load images for $distribution", e)
                withContext(Dispatchers.Main) {
                    _error.value = e.message ?: "Failed to load images"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }
}
