package io.dreamconnected.coa.lxcmanager.ui.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.karbon_exec.launchInternalTerminal
import com.rk.libcommons.TerminalCommand
import io.dreamconnected.coa.lxcmanager.MainActivity
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentContainerOverviewBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.ui.dashboard.DashboardViewModel
import io.dreamconnected.coa.lxcmanager.util.ScreenMask
import io.dreamconnected.coa.lxcmanager.util.ShellClient

class ContainerOverviewFragment : BaseFragment(), MenuProvider {

    private var _binding: FragmentContainerOverviewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var containerName: String? = null
    private lateinit var syncer: SyncContainerStatus
    private var isUserInitiatedChange = false
    private var isFreeze = false
    private var shellClient = ShellClient.instance ?: throw IllegalStateException("ShellClient not initialized")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this)[DashboardViewModel::class.java]

        _binding = FragmentContainerOverviewBinding.inflate(inflater, container, false)
        val root: View = binding.root
        arguments?.let { containerName = it.getString("container_name") }

        setupAppBar(root)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        initChart2(view)

        val lineChart2 = view.findViewById<LineChart>(R.id.lxc_network_chart)
        val statusBar = view.findViewById<MaterialSwitch>(R.id.main_switch_bar)
        syncer = SyncContainerStatus(object : OnSyncListener {
            override fun onSync(status: String, cpu: Float, mem: Float) {
                println("容器状态: $status, CPU: $cpu, 内存: $mem")
                if (status == "FROZEN") {
                    statusBar.isEnabled = false
                    statusBar.text = getString(R.string.co_container_status_frozen)
                } else { onNewData(cpu,mem,lineChart2) }
                when (status) {
                    "RUNNING" -> {
                        statusBar.isEnabled = true
                        statusBar.isChecked = true
                        statusBar.text = getString(R.string.co_container_status_started)
                        isFreeze = false
                        syncer.resume()
                    }
                    "STOPPED" -> {
                        statusBar.isEnabled = true
                        statusBar.isChecked = false
                        statusBar.text = getString(R.string.co_container_status_stopped)
                        isFreeze = false
                        syncer.pause()
                    }
                    else -> {
                        statusBar.isChecked = false
                        statusBar.isEnabled = false
                        statusBar.text = getString(R.string.co_container_status_frozen)
                        isFreeze = true
                        syncer.pause()
                    }
                }
            }
        })

        containerName?.let { syncer.start(it, 5000) }
        syncer.startStatusCheck()

        val lxcFreeze = view.findViewById<ImageButton>(R.id.lxc_freeze)
        val lxcAttach = view.findViewById<ImageButton>(R.id.lxc_attach)
        val lxcConsole = view.findViewById<ImageButton>(R.id.lxc_console)
        val lxcCopy = view.findViewById<ImageButton>(R.id.lxc_copy)
        val lxcSnapshot = view.findViewById<ImageButton>(R.id.lxc_snapshot)
        val lxcDestroy = view.findViewById<ImageButton>(R.id.lxc_destroy)
        val screenMask = ScreenMask(requireContext())

        val clickListener = View.OnClickListener { view ->
            when (view.id) {
                R.id.lxc_freeze -> {
                    screenMask.show()
                    if (isFreeze) {
                        shellClient.execCommand("lxc-unfreeze $containerName", 1, object : ShellClient.CommandOutputListener {
                            override fun onOutput(output: String?) {
                                output?.let {
                                }
                            }
                            override fun onCommandComplete(code: String?) {
                                syncer.resume()
                                screenMask.dismiss()
                            }
                        })
                    } else {
                        shellClient.execCommand("lxc-freeze $containerName", 1, object : ShellClient.CommandOutputListener {
                            override fun onOutput(output: String?) {
                                output?.let {
                                }
                            }
                            override fun onCommandComplete(code: String?) {
                                syncer.pause()
                                screenMask.dismiss()
                            }
                        })
                    }
                }
                R.id.lxc_attach -> {
                    val terminalCommand = TerminalCommand(false,"sh",emptyArray(),"lxc-attach $containerName",2,true,"/data/share",arrayOf("PATH=/data/share/bin:/system/bin","HOME=/data/share","LXC_CMD=lxc-attach","LXC_ARG=$containerName"))
                    launchInternalTerminal(requireContext(),terminalCommand)
                }
                R.id.lxc_console -> {
                    val terminalCommand = TerminalCommand(false,"sh",emptyArray(),"lxc-console $containerName",2,true,"/data/share",arrayOf("PATH=/data/share/bin:/system/bin","HOME=/data/share","LXC_CMD=lxc-console","LXC_ARG=$containerName"))
                    launchInternalTerminal(requireContext(),terminalCommand)
                }
                R.id.lxc_copy -> {
                    screenMask.showInputDialog(requireContext(),"Copy",
                        onConfirm = { inputText ->
                            shellClient.execCommand("lxc-copy $containerName -N $inputText", 5, object : ShellClient.CommandOutputListener {
                                override fun onOutput(output: String?) {
                                    output?.let {
                                        screenMask.showUniqueTextDialog(requireContext(),"Copy",output)
                                    }
                                }
                                override fun onCommandComplete(code: String?) {
                                    code?.let {
                                        if (it.contains("EXITCODE 0")) {
                                            Toast.makeText(requireContext(), "OK", Toast.LENGTH_LONG).show()
                                            screenMask.dismissUniqueTextDialog("Copy")
                                        } else {
                                            screenMask.dismissUniqueTextDialog("Copy", 5)
                                        }
                                    }

                                }
                            })
                        },
                        onCancel = { })
                }
                R.id.lxc_snapshot -> {

                }
                R.id.lxc_destroy -> {

                }
                R.id.main_switch_bar -> {
                    screenMask.show()
                    if (statusBar.text == "Started") {
                        shellClient.execCommand("lxc-stop $containerName", 1, object : ShellClient.CommandOutputListener {
                            override fun onOutput(output: String?) {
                                output?.let {
                                }
                            }
                            override fun onCommandComplete(code: String?) {
                                syncer.resume()
                                screenMask.dismiss()
                            }
                        })
                    }
                    else if (statusBar.text == "Stopped") {
                        shellClient.execCommand("lxc-start $containerName", 1, object : ShellClient.CommandOutputListener {
                            override fun onOutput(output: String?) {
                                output?.let {
                                }
                            }
                            override fun onCommandComplete(code: String?) {
                                syncer.resume()
                                screenMask.dismiss()
                            }
                        })
                    }
                    else {
                        Toast.makeText(requireContext(), "This toast shouldn't be there. It's a bug.", Toast.LENGTH_LONG).show()
                        screenMask.dismiss()
                    }
                }
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

    private fun initChart2(binding: View) {
        val lineChart2 = binding.findViewById<LineChart>(R.id.lxc_network_chart)
        lineChart2.description.text = "CPU %/Mem %"

        val numX = 5
        val dataCPU = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val dataMem = floatArrayOf(0f, 0f, 0f, 0f, 0f)

        LxcNetworkChartManager.setLineName1("Mem")
        LxcNetworkChartManager.setLineName2("CPU")
        LxcNetworkChartManager.initData(numX, dataCPU, dataMem)

        val lineData = LxcNetworkChartManager.initDoubleLineChart(lineChart2)
        LxcNetworkChartManager.initDataStyle(lineChart2, lineData)
    }

    fun onNewData(newValue1: Float, newValue2: Float, lineChart2: LineChart) {
        LxcNetworkChartManager.addEntry(newValue1, newValue2)
        val lineData = LxcNetworkChartManager.initDoubleLineChart(lineChart2)
        LxcNetworkChartManager.initDataStyle(lineChart2, lineData)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_overview, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val dialogView = layoutInflater.inflate(R.layout.dialog_home_about, null)
        return when (menuItem.itemId) {
            R.id.action_export -> {
                MaterialAlertDialogBuilder(requireContext()).setView(dialogView).show()
                true
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.hideBottomNavigation()
        syncer.resume()
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.showBottomNavigation()
        syncer.pause()
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = containerName
        val toolbar = binding.findViewById<Toolbar>(R.id.toolbar)
        (toolbar.context as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syncer.stop()
    }
}