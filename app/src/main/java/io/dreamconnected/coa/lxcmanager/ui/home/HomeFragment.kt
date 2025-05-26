package io.dreamconnected.coa.lxcmanager.ui.home

import android.os.Bundle
import android.util.Log
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
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentHomeBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.util.ShellClient

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

        val actionMainWarringButton = binding.actionMainWarring
        actionMainWarringButton.setOnClickListener {
//            val terminalCommand = TerminalCommand(false,"env",arrayOf(""),"2",2,false,"/sdcard",arrayOf(""))
//            launchInternalTerminal(requireContext(),terminalCommand)
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        var shellClient = ShellClient.instance ?: throw IllegalStateException("ShellClient not initialized")
        shellClient.execCommand("lxc-ls --version", 1, object : ShellClient.CommandOutputListener {
            override fun onOutput(output: String?) {
                Log.d(ShellClient.TAG, "Command Output: $output")
                binding.lxcVer.text=output
            }
            override fun onCommandComplete(code: String?) {
            }
        })

        shellClient.execCommand("file $(command -v lxc-ls) | sed \"s|^.*: ||\"", 1, object : ShellClient.CommandOutputListener {
            override fun onOutput(output: String?) {
                binding.lxcBuildInfo.text=output
            }
            override fun onCommandComplete(code: String?) {
            }
        })
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_home)
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