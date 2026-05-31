package io.dreamconnected.coa.lxcmanager.ui.overview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import io.dreamconnected.coa.lxcmanager.MainActivity
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentContainerOverviewChildBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.util.ScreenMask
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import io.github.coap.lxc.LxcContainer
import io.github.coap.lxc.LxcManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContainerOverviewChildFragment : BaseFragment() {

    private val TAG = "ContainerOverviewChildFragment"

    private var _binding: FragmentContainerOverviewChildBinding? = null
    private val binding get() = _binding!!
    private var containerName: String? = null
    private var statusMonitor: ContainerStatusMonitor? = null
    private var isFreeze = false
    private var lxcManager: LxcManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContainerOverviewChildBinding.inflate(inflater, container, false)
        arguments?.let { containerName = it.getString("container_name") }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initChart2(view)

        val lineChart2 = view.findViewById<LineChart>(R.id.lxc_network_chart)
        val statusBar = view.findViewById<MaterialSwitch>(R.id.main_switch_bar)

        containerName?.let { name ->
            lxcManager = (requireActivity() as MainActivity).getLxcManager()
            statusMonitor = ContainerStatusMonitor(lxcManager, name)

            lifecycleScope.launch {
                statusMonitor?.statusFlow?.collectLatest { containerStatus ->
                    updateStatusUI(containerStatus, statusBar, lineChart2)
                }
            }

            statusMonitor?.startMonitoring(5000L)
        }

        setupClickListeners(view, statusBar)
    }

    private fun updateStatusUI(status: ContainerStatus, statusBar: MaterialSwitch, lineChart2: LineChart) {
        when (status.status) {
            "RUNNING" -> {
                if (!statusBar.isChecked || statusBar.text != getString(R.string.co_container_status_started)) {
                    statusBar.isEnabled = true
                    statusBar.isChecked = true
                    statusBar.text = getString(R.string.co_container_status_started)
                }
                isFreeze = false
                onNewData(status.cpu, status.mem, lineChart2)
            }
            "STOPPED" -> {
                if (statusBar.isChecked || statusBar.text != getString(R.string.co_container_status_stopped)) {
                    statusBar.isEnabled = true
                    statusBar.isChecked = false
                    statusBar.text = getString(R.string.co_container_status_stopped)
                }
                isFreeze = false
            }
            "FROZEN" -> {
                if (statusBar.isEnabled || statusBar.text != getString(R.string.co_container_status_frozen)) {
                    statusBar.isEnabled = false
                    statusBar.isChecked = false
                    statusBar.text = getString(R.string.co_container_status_frozen)
                }
                isFreeze = true
            }
            else -> {
                if (statusBar.isEnabled || statusBar.text != getString(R.string.co_container_status_frozen)) {
                    statusBar.isChecked = false
                    statusBar.isEnabled = false
                    statusBar.text = getString(R.string.co_container_status_frozen)
                }
                isFreeze = true
            }
        }
    }

    private fun setupClickListeners(view: View, statusBar: MaterialSwitch) {
        val lxcFreeze = view.findViewById<ImageButton>(R.id.lxc_freeze)
        val lxcAttach = view.findViewById<ImageButton>(R.id.lxc_attach)
        val lxcConsole = view.findViewById<ImageButton>(R.id.lxc_console)
        val lxcCopy = view.findViewById<ImageButton>(R.id.lxc_copy)
        val lxcSnapshot = view.findViewById<ImageButton>(R.id.lxc_snapshot)
        val lxcDestroy = view.findViewById<ImageButton>(R.id.lxc_destroy)
        val screenMask = ScreenMask(requireContext())

        val clickListener = View.OnClickListener { v ->
            when (v.id) {
                R.id.lxc_freeze -> handleFreezeClick(screenMask)
                R.id.lxc_attach -> handleAttachClick()
                R.id.lxc_console -> handleConsoleClick()
                R.id.lxc_copy -> handleCopyClick(screenMask)
                R.id.lxc_snapshot -> handleSnapshotClick()
                R.id.lxc_destroy -> handleDestroyClick()
                R.id.main_switch_bar -> handleStatusSwitchClick(statusBar, screenMask)
            }
        }

        lxcFreeze.setOnClickListener(clickListener)
        lxcAttach.setOnClickListener(clickListener)
        lxcConsole.setOnClickListener(clickListener)
        lxcCopy.setOnClickListener(clickListener)
        lxcSnapshot.setOnClickListener(clickListener)
        lxcDestroy.setOnClickListener(clickListener)
        statusBar.setOnClickListener(clickListener)
    }

    private fun handleFreezeClick(screenMask: ScreenMask) {
        screenMask.show()
        containerName?.let { name ->
            val container = lxcManager?.getContainer(name)
            if (container == null) {
                Log.e(TAG, "Container '$name' not found")
                screenMask.dismiss()
                return
            }

            val success = if (isFreeze) {
                container.unfreeze()
            } else {
                container.freeze()
            }

            if (success) {
                Log.d(TAG, "Successfully ${if (isFreeze) "unfreeze" else "freeze"} container '$name'")
                Toast.makeText(requireContext(), "Operation successful", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to ${if (isFreeze) "unfreeze" else "freeze"} container '$name'")
                Toast.makeText(requireContext(), "Operation failed", Toast.LENGTH_SHORT).show()
            }
        }
        screenMask.dismiss()
    }

    private fun handleAttachClick() {
        containerName?.let { name ->
            val terminalCommand = TerminalCommand(
                false, "sh", emptyArray(), "lxc-attach $name", 2, true, "/data/share",
                arrayOf("PATH=/data/share/bin:/system/bin", "HOME=/data/share", "LD_LIBRARY_PATH=/data/share/lib:/system/lib64", "LXC_CMD=lxc-attach", "LXC_ARG=$name")
            )
            launchInternalTerminal(requireContext(), terminalCommand)
        }
    }

    private fun handleConsoleClick() {
        containerName?.let { name ->
            val terminalCommand = TerminalCommand(
                false, "sh", emptyArray(), "lxc-console $name", 2, true, "/data/share",
                arrayOf("PATH=/data/share/bin:/system/bin", "HOME=/data/share", "LD_LIBRARY_PATH=/data/share/lib:/system/lib64", "LXC_CMD=lxc-console", "LXC_ARG=$name")
            )
            launchInternalTerminal(requireContext(), terminalCommand)
        }
    }

    private fun handleCopyClick(screenMask: ScreenMask) {
        screenMask.showInputDialog(requireContext(), "Copy",
            onConfirm = { inputText ->
                ShellCommandExecutor.execCommand("lxc-copy $containerName -N $inputText", object : ShellCommandExecutor.CommandOutputListener {
                    override fun onOutput(output: String?) {
                        output?.let { screenMask.showUniqueTextDialog(requireContext(), "Copy", it) }
                    }
                    override fun onCommandComplete(success: Boolean, exitCode: Int, output: String?) {
                        if (success) {
                            Toast.makeText(requireContext(), "OK", Toast.LENGTH_LONG).show()
                            screenMask.dismissUniqueTextDialog("Copy")
                        } else {
                            screenMask.dismissUniqueTextDialog("Copy", 5)
                        }
                    }
                })
            },
            onCancel = { })
    }

    private fun handleSnapshotClick() {}

    private fun handleDestroyClick() {
        containerName?.let { name ->
            val container = lxcManager?.getContainer(name)
            if (container == null) {
                Log.e(TAG, "Container '$name' not found")
                Toast.makeText(requireContext(), "Container not found", Toast.LENGTH_SHORT).show()
                return
            }

            val success = container.destroy()
            if (success) {
                Log.d(TAG, "Successfully destroyed container '$name'")
                Toast.makeText(requireContext(), "Container destroyed", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to destroy container '$name'")
                Toast.makeText(requireContext(), "Failed to destroy container", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleStatusSwitchClick(statusBar: MaterialSwitch, screenMask: ScreenMask) {
        screenMask.show()
        containerName?.let { name ->
            val container = lxcManager?.getContainer(name)
            if (container == null) {
                Log.e(TAG, "Container '$name' not found")
                screenMask.dismiss()
                return@let
            }

            val success = when (statusBar.text) {
                getString(R.string.co_container_status_started) -> container.stop()
                getString(R.string.co_container_status_stopped) -> container.start()
                else -> false
            }

            if (success) {
                Log.d(TAG, "Successfully changed container status for '$name'")
                Toast.makeText(requireContext(), "Status changed", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to change container status for '$name'")
                Toast.makeText(requireContext(), "Failed to change status", Toast.LENGTH_SHORT).show()
            }
        }
        screenMask.dismiss()
    }

    private fun initChart2(view: View) {
        val lineChart2 = view.findViewById<LineChart>(R.id.lxc_network_chart)
        lineChart2.description.text = "CPU %/Mem %"

        val numX = 5
        val dataCPU = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val dataMem = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        LxcCpuMemChartManager.setLineName1("Mem")
        LxcCpuMemChartManager.setLineName2("CPU")
        LxcCpuMemChartManager.initData(numX, dataCPU, dataMem)

        val lineData = LxcCpuMemChartManager.initDoubleLineChart(lineChart2)
        LxcCpuMemChartManager.initDataStyle(lineChart2, lineData)
    }

    fun onNewData(newValue1: Float, newValue2: Float, lineChart2: LineChart) {
        Log.d("ContainerOverview", "onNewData: Mem=$newValue1, CPU=$newValue2")
        requireActivity().runOnUiThread {
            LxcCpuMemChartManager.addEntry(newValue1, newValue2)
            LxcCpuMemChartManager.updateChartData(lineChart2)
            Log.d("ContainerOverview", "onNewData: Chart updated on UI thread")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusMonitor?.stopMonitoring()
        _binding = null
    }
}
