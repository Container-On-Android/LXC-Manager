package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.coap.lxc.LxcContainer
import io.github.coap.lxc.LxcManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class DashboardViewModel : ViewModel() {

    private val _items = MutableLiveData<MutableList<Item>>(mutableListOf())
    val items: LiveData<MutableList<Item>> = _items
    private val refreshCounter = AtomicInteger(0)
    private var lxcManager: LxcManager? = null
    private var refreshJob: Job? = null

    fun setLxcManager(manager: LxcManager?) {
        this.lxcManager = manager
    }

    fun refreshContainers() {
        refreshJob?.cancel()
        val requestId = refreshCounter.incrementAndGet()
        
        refreshJob = viewModelScope.launch {
            val manager = lxcManager ?: run {
                _items.postValue(mutableListOf())
                return@launch
            }

            try {
                val containers = withContext(Dispatchers.IO) {
                    manager.listContainers()
                }
                
                if (requestId != refreshCounter.get()) return@launch

                val initialItems = containers.map { container ->
                    Item(
                        name = container.name,
                        state = "LOADING",
                        autostart = "LOADING",
                        groups = "LOADING",
                        ipv4 = null,
                        ipv6 = null,
                        unprivileged = "LOADING"
                    )
                }.toMutableList()
                _items.postValue(initialItems)

                for ((index, container) in containers.withIndex()) {
                    if (requestId != refreshCounter.get()) break
                    
                    val detailedItem = withContext(Dispatchers.IO) {
                        containerToItem(container)
                    }
                    
                    if (requestId != refreshCounter.get()) break

                    val currentList = _items.value?.toMutableList() ?: continue
                    if (index < currentList.size && currentList[index].name == detailedItem.name) {
                        currentList[index] = detailedItem
                        _items.postValue(currentList)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error getting containers", e)
            }
        }
    }

    private fun containerToItem(container: LxcContainer): Item {
        val name = container.name
        val state = container.state
        val autostart = container.getConfigItem("lxc.start.auto") ?: "NULL"
        val groups = container.getConfigItem("lxc.group") ?: "default"
        val ipv4 = getAddresses(container, "inet")
        val ipv6 = getAddresses(container, "inet6")
        val unprivileged = container.getConfigItem("lxc.idmap")?.let { "true" } ?: "false"

        return Item(
            name = name,
            state = state,
            autostart = autostart,
            groups = groups,
            ipv4 = ipv4,
            ipv6 = ipv6,
            unprivileged = unprivileged
        )
    }

    private fun getAddresses(container: LxcContainer, family: String): String? {
        val interfaces = container.interfaces
        val addresses = mutableListOf<String>()
        for (iface in interfaces) {
            val ips = container.getIps(iface, family, 0)
            for (ip in ips) {
                val isLoopback = if (family == "inet") ip.startsWith("127.") else ip == "::1"
                if (ip.isNotEmpty() && !isLoopback && !addresses.contains(ip)) {
                    addresses.add(ip)
                }
            }
        }
        return if (addresses.isEmpty()) null else addresses.joinToString(", ")
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
