package io.dreamconnected.coa.lxcmanager.ui.repos

import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ReposViewModel : ViewModel() {

    private val TAG = "ReposViewModel"
    private val BASE_URL = "https://images.linuxcontainers.org"
    private val INDEX_PATH = "/meta/1.0/index-system"
    private val USER_AGENT = "lxc/1.0 compat:7"
    private val THREAD_STATS_TAG = 0xF00D

    private val _images = MutableLiveData<List<ImageItem>>()
    val images: LiveData<List<ImageItem>> = _images

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectedDistribution = MutableLiveData<String>()
    val selectedDistribution: LiveData<String> = _selectedDistribution

    private val _distributions = MutableLiveData<List<String>>()
    val distributions: LiveData<List<String>> = _distributions

    fun loadDistributions() {
        _isLoading.value = true
        _error.value = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TrafficStats.setThreadStatsTag(THREAD_STATS_TAG)
                val url = URL("$BASE_URL$INDEX_PATH")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val distributionSet = mutableSetOf<String>()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            val parts = it.split(";")
                            if (parts.size >= 1 && parts[0].isNotEmpty()) {
                                distributionSet.add(parts[0])
                            }
                        }
                    }
                    reader.close()

                    val dists = distributionSet.sorted()

                    withContext(Dispatchers.Main) {
                        _distributions.value = dists
                        if (dists.isNotEmpty()) {
                            _selectedDistribution.value = dists[0]
                            loadImages(dists[0])
                        }
                    }
                } else {
                    throw Exception("HTTP error code: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load distributions", e)
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
        _isLoading.value = true
        _error.value = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TrafficStats.setThreadStatsTag(THREAD_STATS_TAG)
                val url = URL("$BASE_URL$INDEX_PATH")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
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
                            if (parts.size >= 4 && parts[0] == distribution) {
                                val release = parts[1]
                                val arch = parts[2]
                                val variant = parts[3]
                                val fullPath = "$distribution/$release/$arch/$variant"
                                imageList.add(
                                    ImageItem(
                                        distribution = distribution,
                                        release = release,
                                        architecture = arch,
                                        variant = variant,
                                        fullPath = fullPath
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
                Log.e(TAG, "Failed to load images for $distribution", e)
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