package io.dreamconnected.coa.lxcmanager.ui.repos

import android.app.Application
import android.content.SharedPreferences
import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
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

class ReposViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ReposViewModel"
    private val baseUrl = "https://images.linuxcontainers.org"
    private val indexPath = "/meta/1.0/index-system"
    private val userAgent = "lxc/1.0 compat:7"
    private val threadStatsTag = 0xF00D
    private val cacheExpiryDays = 1L

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("repos_cache", android.content.Context.MODE_PRIVATE)
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

    private val _distributions = MutableLiveData<List<String>>()
    val distributions: LiveData<List<String>> = _distributions

    private fun isCacheValid(): Boolean {
        val lastCacheTime = prefs.getLong("last_cache_time", 0L)
        val cacheExpiryMillis = TimeUnit.DAYS.toMillis(cacheExpiryDays)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastCacheTime) < cacheExpiryMillis
    }

    private fun saveCache(distributions: List<String>, images: List<ImageItem>) {
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

    private fun loadFromCache(): Pair<List<String>, List<ImageItem>>? {
        if (!isCacheValid()) {
            return null
        }

        val distributionsJson = prefs.getString("cached_distributions", null) ?: return null
        val imagesJson = prefs.getString("cached_images", null) ?: return null

        return try {
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

            Pair(distributions, images)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load from cache", e)
            null
        }
    }

    fun loadDistributions() {
        _isLoading.value = true
        _error.value = null

        CoroutineScope(Dispatchers.IO).launch {
            val cachedData = loadFromCache()
            if (cachedData != null) {
                withContext(Dispatchers.Main) {
                    _distributions.value = cachedData.first
                    _images.value = cachedData.second
                    if (cachedData.first.isNotEmpty()) {
                        _selectedDistribution.value = cachedData.first[0]
                    }
                    _isLoading.value = false
                }
                return@launch
            }

            try {
                TrafficStats.setThreadStatsTag(threadStatsTag)
                val url = URL("$baseUrl$indexPath")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", userAgent)
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
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

                    val dists = distributionSet.sorted()
                    allImages.sortWith(compareBy({ it.release }, { it.architecture }, { it.variant }))
                    saveCache(dists, allImages)

                    withContext(Dispatchers.Main) {
                        _distributions.value = dists
                        _images.value = allImages
                        if (dists.isNotEmpty()) {
                            _selectedDistribution.value = dists[0]
                        }
                    }
                } else {
                    throw Exception("HTTP error code: $responseCode")
                }
                connection.disconnect()
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

    fun loadImages(distribution: String) {
        _selectedDistribution.value = distribution

        CoroutineScope(Dispatchers.IO).launch {
            val cachedData = loadFromCache()
            if (cachedData != null) {
                val filteredImages = cachedData.second.filter { it.distribution == distribution }
                withContext(Dispatchers.Main) {
                    _images.value = filteredImages
                }
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                TrafficStats.setThreadStatsTag(threadStatsTag)
                val url = URL("$baseUrl$indexPath")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", userAgent)
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
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

                    imageList.sortWith(compareBy({ it.release }, { it.architecture }, { it.variant }))

                    withContext(Dispatchers.Main) {
                        _images.value = imageList
                    }
                } else {
                    throw Exception("HTTP error code: $responseCode")
                }
                connection.disconnect()
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