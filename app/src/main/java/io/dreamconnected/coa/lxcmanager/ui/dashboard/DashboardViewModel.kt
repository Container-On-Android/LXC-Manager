package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.github.coap.lxc.LxcContainer
import io.github.coap.lxc.LxcManager
import java.util.concurrent.atomic.AtomicInteger

class DashboardViewModel : ViewModel() {

    private val _items = MutableLiveData<MutableList<Item>>(mutableListOf())
    val items: LiveData<MutableList<Item>> = _items
    private val refreshCounter = AtomicInteger(0)
    private var lxcManager: LxcManager? = null

    fun setLxcManager(manager: LxcManager?) {
        this.lxcManager = manager
    }

    fun refreshContainers() {
        val requestId = refreshCounter.incrementAndGet()
        val tempItems = mutableListOf<Item>()

        lxcManager?.let { manager ->
            try {
                val containers = manager.listContainers()
                for (container in containers) {
                    if (requestId != refreshCounter.get()) return
                    val item = containerToItem(container)
                    tempItems.add(item)
                }
                _items.postValue(tempItems.toMutableList())
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error getting containers", e)
                _items.postValue(tempItems.toMutableList())
            }
        } ?: run {
            _items.postValue(tempItems.toMutableList())
        }
    }

    private fun containerToItem(container: LxcContainer): Item {
        val name = container.name
        val state = container.state
        val autostart = container.getConfigItem("lxc.start.auto") ?: "false"
        val groups = "default"
        val ipv4 = container.getConfigItem("lxc.net.0.ipv4.address") ?: ""
        val ipv6 = container.getConfigItem("lxc.net.0.ipv6.address") ?: ""
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
}
