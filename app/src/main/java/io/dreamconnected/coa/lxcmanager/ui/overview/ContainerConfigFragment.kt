package io.dreamconnected.coa.lxcmanager.ui.overview

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.dreamconnected.coa.lxcmanager.MainActivity
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentContainerConfigBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import io.github.coap.lxc.LxcManager

class ContainerConfigFragment : BaseFragment() {

    private val TAG = "ContainerConfigFragment"
    private var _binding: FragmentContainerConfigBinding? = null
    private val binding get() = _binding!!
    private var containerName: String? = null
    private var lxcManager: LxcManager? = null
    private var configContent: String = ""
    private var hasChanges: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentContainerConfigBinding.inflate(inflater, container, false)
        arguments?.let { containerName = it.getString("container_name") }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        containerName?.let { name ->
            lxcManager = (requireActivity() as MainActivity).getLxcManager()
            loadConfig(name)
        }

        binding.etConfigContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasChanges = s.toString() != configContent
            }
        })
    }

    private fun loadConfig(containerName: String) {
        val container = lxcManager?.getContainer(containerName)
        if (container == null) {
            binding.etConfigContent.setText("Container not found")
            return
        }

        val configPath = container.configFileName()
        
        val result = ShellCommandExecutor.execCommandSync("cat $configPath")
        
        if (result.isNotEmpty() && !result.contains("cat:") && !result.contains("No such file")) {
            configContent = result
            hasChanges = false
            binding.etConfigContent.setText(result)
        } else {
            binding.etConfigContent.setText("# Failed to load config file\n# Path: $configPath")
        }
    }

    public fun saveConfig() {
        val currentText = binding.etConfigContent.text.toString()
        if (currentText == configContent) {
            Toast.makeText(requireContext(), "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        val container = lxcManager?.getContainer(containerName) ?: return
        val lxcPath = container.getLxcPath()
        val configPath = "$lxcPath/$containerName/config"
        
        showSaveConfirmDialog(configPath, currentText)
    }

    private fun showSaveConfirmDialog(configPath: String, newContent: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Save Configuration")
            .setMessage("Are you sure you want to save the configuration changes?")
            .setPositiveButton("Save") { _, _ ->
                performSave(configPath, newContent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSave(configPath: String, newContent: String) {
        val escapedContent = newContent.replace("'", "'\\''")
        
        ShellCommandExecutor.execCommand("echo '$escapedContent' > $configPath", object : ShellCommandExecutor.CommandOutputListener {
            override fun onOutput(output: String?) {}
            
            override fun onCommandComplete(success: Boolean, exitCode: Int, output: String?) {
                requireActivity().runOnUiThread {
                    if (success) {
                        configContent = newContent
                        hasChanges = false
                        Toast.makeText(requireContext(), "Config saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save config", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
