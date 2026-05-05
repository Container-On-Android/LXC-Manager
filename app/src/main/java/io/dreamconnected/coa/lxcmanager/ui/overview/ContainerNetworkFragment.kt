package io.dreamconnected.coa.lxcmanager.ui.overview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import io.dreamconnected.coa.lxcmanager.MainActivity
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentContainerNetworkBinding
import io.dreamconnected.coa.lxcmanager.databinding.ItemNetworkConfigBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.github.coap.lxc.LxcManager
import java.util.concurrent.ConcurrentHashMap

class ContainerNetworkFragment : BaseFragment() {

    private val TAG = "ContainerNetworkFragment"
    private var _binding: FragmentContainerNetworkBinding? = null
    private val binding get() = _binding!!
    private var containerName: String? = null
    private var lxcManager: LxcManager? = null
    private val networkConfigs = mutableListOf<NetworkConfig>()
    private val pendingChanges = ConcurrentHashMap<String, String>()

    private val networkConfigKeys = listOf(
        "type", "flags", "link", "l2proxy", "mtu", "name",
        "ipv4.address", "ipv4.gateway", "ipv6.address", "ipv6.gateway",
        "script.up", "script.down"
    )

    private val networkConfigLabels = mapOf(
        "type" to "Type",
        "flags" to "Flags",
        "link" to "Link",
        "l2proxy" to "L2 Proxy",
        "mtu" to "MTU",
        "name" to "Name",
        "ipv4.address" to "IPv4 Address",
        "ipv4.gateway" to "IPv4 Gateway",
        "ipv6.address" to "IPv6 Address",
        "ipv6.gateway" to "IPv6 Gateway",
        "script.up" to "Script Up",
        "script.down" to "Script Down"
    )

    private val typeOptions = listOf("none", "empty", "veth", "vlan", "macvlan", "ipvlan", "phys")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentContainerNetworkBinding.inflate(inflater, container, false)
        arguments?.let { containerName = it.getString("container_name") }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        containerName?.let { name ->
            lxcManager = (requireActivity() as MainActivity).getLxcManager()
            loadNetworkConfig(name)
        }
    }

    private fun loadNetworkConfig(containerName: String) {
        val container = lxcManager?.getContainer(containerName)
        if (container == null) {
            Log.e(TAG, "Container not found: $containerName")
            return
        }

        networkConfigs.clear()
        
        var index = 0
        while (true) {
            val type = container.getConfigItem("lxc.net.$index.type")
            if (type.isNullOrEmpty()) {
                if (index == 0) {
                    networkConfigs.add(NetworkConfig("No Network Interface", "No network interfaces configured", -1, ""))
                }
                break
            }

            networkConfigs.add(NetworkConfig("Interface $index", "", index, ""))
            
            for (key in networkConfigKeys) {
                val configKey = "lxc.net.$index.$key"
                val value = container.getConfigItem(configKey)
                if (!value.isNullOrEmpty()) {
                    networkConfigs.add(NetworkConfig(
                        networkConfigLabels[key] ?: key,
                        value,
                        index,
                        configKey
                    ))
                }
            }
            
            index++
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val adapter = NetworkConfigAdapter(networkConfigs) { config ->
            if (config.key.isNotEmpty() && config.interfaceIndex >= 0 && config.configKey.isNotEmpty()) {
                showEditDialog(config)
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }

    public fun showAddInterfaceDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_interface, null)
        val autoCompleteType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.auto_complete_type)
        val editLink = dialogView.findViewById<TextInputEditText>(R.id.edit_link)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.edit_name)
        val editMtu = dialogView.findViewById<TextInputEditText>(R.id.edit_mtu)

        val typeAdapter = ArrayAdapter(requireContext(), R.layout.list_item_type, typeOptions)
        autoCompleteType.setAdapter(typeAdapter)
        autoCompleteType.setText(typeOptions[0], false)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Network Interface")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val type = autoCompleteType.text.toString()
                val link = editLink.text.toString()
                val name = editName.text.toString()
                val mtu = editMtu.text.toString()
                
                addInterface(type, link, name, mtu)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addInterface(type: String, link: String, name: String, mtu: String) {
        val newIndex = getNextInterfaceIndex()
        
        pendingChanges["lxc.net.$newIndex.type"] = type
        
        if (link.isNotEmpty()) {
            pendingChanges["lxc.net.$newIndex.link"] = link
        }
        if (name.isNotEmpty()) {
            pendingChanges["lxc.net.$newIndex.name"] = name
        }
        if (mtu.isNotEmpty()) {
            pendingChanges["lxc.net.$newIndex.mtu"] = mtu
        }

        Toast.makeText(requireContext(), "Changes pending", Toast.LENGTH_SHORT).show()
    }

    private fun getNextInterfaceIndex(): Int {
        var maxIndex = -1
        for (config in networkConfigs) {
            if (config.interfaceIndex > maxIndex) {
                maxIndex = config.interfaceIndex
            }
        }
        return maxIndex + 1
    }

    private fun showEditDialog(config: NetworkConfig) {
        if (config.configKey.isEmpty()) return

        if (config.key == "Type") {
            showTypeEditDialog(config)
        } else {
            showTextEditDialog(config)
        }
    }

    private fun showTypeEditDialog(config: NetworkConfig) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_type, null)
        val autoCompleteType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.auto_complete_type)

        val typeAdapter = ArrayAdapter(requireContext(), R.layout.list_item_type, typeOptions)
        autoCompleteType.setAdapter(typeAdapter)
        autoCompleteType.setText(config.value, false)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Type")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newValue = autoCompleteType.text.toString()
                updateConfig(config.configKey, newValue)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextEditDialog(config: NetworkConfig) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.edit_text)
        editText.setText(config.value)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit ${config.key}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newValue = editText.text.toString()
                updateConfig(config.configKey, newValue)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateConfig(key: String, value: String) {
        pendingChanges[key] = value
        Toast.makeText(requireContext(), "Changes pending", Toast.LENGTH_SHORT).show()
    }

    public fun saveChanges() {
        if (pendingChanges.isEmpty()) {
            Toast.makeText(requireContext(), "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        val container = lxcManager?.getContainer(containerName) ?: return
        var allSuccess = true

        for ((key, value) in pendingChanges) {
            val success = container.setConfigItem(key, value)
            if (!success) {
                Log.e(TAG, "Failed to set config item: $key = $value")
                allSuccess = false
            }
        }

        if (allSuccess) {
            Toast.makeText(requireContext(), "Network config saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Some changes failed", Toast.LENGTH_SHORT).show()
        }

        pendingChanges.clear()
        containerName?.let { loadNetworkConfig(it) }
    }

    data class NetworkConfig(val key: String, val value: String, val interfaceIndex: Int, val configKey: String = "")

    inner class NetworkConfigAdapter(
        private val configs: List<NetworkConfig>,
        private val onItemClick: (NetworkConfig) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<NetworkConfigAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemNetworkConfigBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

            fun bind(config: NetworkConfig) {
                binding.tvKey.text = config.key
                binding.tvValue.text = config.value
                
                if (config.interfaceIndex == -1) {
                    binding.tvKey.setTextColor(resources.getColor(R.color.md_theme_error, null))
                    binding.tvValue.setTextColor(resources.getColor(R.color.md_theme_error, null))
                    binding.root.isClickable = false
                } else if (config.value.isEmpty()) {
                    binding.tvKey.textSize = 16f
                    binding.tvValue.visibility = View.GONE
                    binding.root.isClickable = false
                } else {
                    binding.tvValue.visibility = View.VISIBLE
                    binding.root.isClickable = true
                    binding.root.setOnClickListener { onItemClick(config) }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemNetworkConfigBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(configs[position])
        }

        override fun getItemCount(): Int = configs.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
