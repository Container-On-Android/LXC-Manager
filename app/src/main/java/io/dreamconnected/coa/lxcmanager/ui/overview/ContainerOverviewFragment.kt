package io.dreamconnected.coa.lxcmanager.ui.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentContainerOverviewBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.ui.dashboard.DashboardViewModel

class ContainerOverviewFragment : BaseFragment(), MenuProvider {

    private var _binding: FragmentContainerOverviewBinding? = null
    private val binding get() = _binding!!
    private var containerName: String? = null

    private var currentPosition: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        _binding = FragmentContainerOverviewBinding.inflate(inflater, container, false)
        val root: View = binding.root
        arguments?.let { containerName = it.getString("container_name") }
        setupAppBar(root)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val viewPager = binding.viewPager

        viewPager.adapter = ViewPagerAdapter(this)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateFabForPosition(position)
            }
        })

        TabLayoutMediator(binding.tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.overview)
                1 -> getString(R.string.network)
                2 -> getString(R.string.config)
                else -> getString(R.string.overview)
            }
        }.attach()

        setupFabListeners()
        updateFabForPosition(0)
    }

    private fun setupFabListeners() {
        binding.fabPrimary.setOnClickListener {
            when (currentPosition) {
                0 -> {
                }
                1 -> {
                    (getChildFragmentAtPosition(1) as? ContainerNetworkFragment)?.showAddInterfaceDialog()
                }
                2 -> {
                    (getChildFragmentAtPosition(2) as? ContainerConfigFragment)?.saveConfig()
                }
            }
        }

        binding.fabSecondary.setOnClickListener {
            when (currentPosition) {
                0 -> {
                }
                1 -> {
                    (getChildFragmentAtPosition(1) as? ContainerNetworkFragment)?.saveChanges()
                }
                2 -> {
                }
            }
        }
    }

    private fun getChildFragmentAtPosition(position: Int): Fragment? {
        val adapter = binding.viewPager.adapter as? ViewPagerAdapter ?: return null
        return adapter.getFragmentAtPosition(position)
    }

    private fun updateFabForPosition(position: Int) {
        when (position) {
            0 -> {
                binding.fabPrimary.visibility = View.GONE
                binding.fabSecondary.visibility = View.GONE
            }
            1 -> {
                binding.fabPrimary.visibility = View.VISIBLE
                binding.fabSecondary.visibility = View.VISIBLE
                binding.fabPrimary.setImageResource(R.drawable.fab_add_large)
                binding.fabPrimary.contentDescription = getString(R.string.lxc_copy)
                binding.fabSecondary.setImageResource(R.drawable.fab_add_large)
                binding.fabSecondary.contentDescription = getString(R.string.action_overview_export)
            }
            2 -> {
                binding.fabPrimary.visibility = View.VISIBLE
                binding.fabSecondary.visibility = View.GONE
                binding.fabPrimary.setImageResource(R.drawable.fab_add_large)
                binding.fabPrimary.contentDescription = getString(R.string.action_overview_export)
            }
        }
    }

    private inner class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            val fragment = when (position) {
                0 -> {
                    ContainerOverviewChildFragment().also {
                        val args = Bundle()
                        args.putString("container_name", containerName)
                        it.arguments = args
                    }
                }
                1 -> {
                    ContainerNetworkFragment().also {
                        val args = Bundle()
                        args.putString("container_name", containerName)
                        it.arguments = args
                    }
                }
                2 -> {
                    ContainerConfigFragment().also {
                        val args = Bundle()
                        args.putString("container_name", containerName)
                        it.arguments = args
                    }
                }
                else -> {
                    ContainerOverviewChildFragment().also {
                        val args = Bundle()
                        args.putString("container_name", containerName)
                        it.arguments = args
                    }
                }
            }
            fragments[position] = fragment
            return fragment
        }

        fun getFragmentAtPosition(position: Int): Fragment? = fragments[position]
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
        (activity as? io.dreamconnected.coa.lxcmanager.MainActivity)?.hideBottomNavigation()
    }

    override fun onPause() {
        super.onPause()
        (activity as? io.dreamconnected.coa.lxcmanager.MainActivity)?.showBottomNavigation()
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout = binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = containerName
        val toolbar = binding.findViewById<Toolbar>(R.id.toolbar)
        (toolbar.context as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
