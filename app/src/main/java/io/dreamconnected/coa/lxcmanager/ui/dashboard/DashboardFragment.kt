package io.dreamconnected.coa.lxcmanager.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentDashboardBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.ui.dashboard.ItemAdapter.OnItemClickListener
import io.dreamconnected.coa.lxcmanager.util.LxcTemplates
import io.dreamconnected.coa.lxcmanager.util.ScreenMask

class DashboardFragment : BaseFragment(),OnItemClickListener {

    private var _binding: FragmentDashboardBinding? = null
    private lateinit var adapter: ItemAdapter

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this)[DashboardViewModel::class.java]

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root
        setupAppBar(root)

        val recyclerView: RecyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ItemAdapter(mutableListOf(),this)
        recyclerView.adapter = adapter

        dashboardViewModel.items.observe(viewLifecycleOwner) { items ->
            adapter.setItems(items)
        }
        return root
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_dashboard)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fabAddFromBackup.setOnClickListener {  }
        binding.fabAddFromTemplate.setOnClickListener {
            val templates = listOf(
                LxcTemplates("download", listOf("dist", "release", "arch")),
                LxcTemplates("busybox", listOf("busybox-path"))
            )
            ScreenMask(requireContext()).showTemplateSelectionDialog(requireContext(),templates)
        }
    }

    override fun onItemClick(item: String) {
        val bundle = Bundle().apply {
            putString("container_name", item)
        }

        val navController = findNavController()
        navController.navigate(R.id.navigation_container_overview, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}