package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import java.util.concurrent.atomic.AtomicInteger

class DashboardViewModel : ViewModel() {

    private val _items = MutableLiveData<MutableList<Item>>(mutableListOf())
    val items: LiveData<MutableList<Item>> = _items
    private val refreshCounter = AtomicInteger(0)

    fun refreshContainers() {
        val requestId = refreshCounter.incrementAndGet()
        val tempItems = mutableListOf<Item>()
        ShellCommandExecutor.execCommand("lxc-ls -f | tail -n +2", object : ShellCommandExecutor.CommandOutputListener {
            override fun onOutput(output: String?) {
                if (requestId != refreshCounter.get()) return
                output?.let {
                    val line = it.trim()
                    if (line.isNotEmpty()) {
                        try {
                            val item = parseContainerData(line)
                            tempItems.add(item)
                        } catch (e: IllegalArgumentException) {
                            Log.e("DashboardViewModel", "Error parsing line: $line", e)
                        }
                    }
                }
            }

            override fun onCommandComplete(code: String?) {
                if (requestId != refreshCounter.get()) return
                _items.postValue(tempItems.toMutableList())
            }
        })
    }

    fun parseContainerData(data: String): Item {
        val parts = data.trim().split(Regex("\\s+"))

        if (parts.size < 7) {
            throw IllegalArgumentException("Invalid data format")
        }

        val name = parts[0]
        val state = parts[1]
        val autostart = parts[2]
        val groups = parts[3]

        val ipv4List = mutableListOf<String>()
        val ipv6List = mutableListOf<String>()

        for (i in 4 until parts.size - 1) {
            val part = parts[i]
            if (part.contains(",")) {
                ipv4List.addAll(part.split(",").map { it.trim() }.filter { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) })
            } else if (part.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                ipv4List.add(part)
            } else if (part.contains(":")) {
                ipv6List.add(part)
            }
        }

        val unprivileged = parts.last()

        return Item(
            name = name,
            state = state,
            autostart = autostart,
            groups = groups,
            ipv4 = ipv4List.joinToString("\n"),
            ipv6 = ipv6List.joinToString("\n"),
            unprivileged = unprivileged
        )
    }
}
