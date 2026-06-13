package io.dreamconnected.coa.lxcmanager.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentHomeBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.util.MessageCardManager
import io.dreamconnected.coa.lxcmanager.util.MessageCardManager.Companion.addMessage
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor.execCommandSync
import io.github.coap.lxc.LxcNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeFragment : BaseFragment(), MenuProvider {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.deviceCgroup
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        setupAppBar(root)

        MessageCardManager.attachContainer(binding.messageCardContainer)
        setupMessage()

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val version = LxcNative.getVersion()
        binding.lxcVer.text = version ?: "Null"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val mountOutput = execCommandSync("mount | grep cgroup")
            val cgrouprcOutput = execCommandSync("strings /dev/cgroup_info/cgroup.rc")
            val cgroupsjsonOutput = execCommandSync("cat /system/etc/cgroups.json")

            withContext(Dispatchers.Main) {
                _binding?.let { binding ->
                    binding.deviceCgroup.text = buildString {
                        appendLine("=== Mount (cgroup) ===")
                        appendLine(mountOutput)
                        appendLine()
                        appendLine("=== cgroup.rc ===")
                        appendLine(cgrouprcOutput)
                        appendLine()
                        appendLine("=== cgroups.json ===")
                        appendLine(cgroupsjsonOutput)
                    }
                }
            }
        }
    }
    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_home)
    }

    fun setupMessage() {
        Shell.isAppGrantedRoot()?.let {
            if (!it) {
                addMessage("ERROR","Shell Service",
                    requireContext().getString(R.string.root_grant_err_no_su),"OK") { messageId ->
                    MessageCardManager.removeMessage(messageId)
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_home, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_home_about, null)
        return when (menuItem.itemId) {
            R.id.action_issue -> {
                true
            }
            R.id.action_about -> {
                MaterialAlertDialogBuilder(requireContext()).setView(dialogView).show()
                true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}