package io.dreamconnected.coa.lxcmanager.ui.repos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentReposBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.ui.download.DownloadViewModel

class ReposFragment : BaseFragment(), ImageAdapter.OnImageClickListener {

    private var _binding: FragmentReposBinding? = null
    private lateinit var adapter: ImageAdapter
    private lateinit var reposViewModel: ReposViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private var isSettingSelection = false
    private var lastDistributions: List<String> = emptyList()
    private var lastArchitectures: List<String>? = null

    private val binding get() = _binding!!

    private val pickContentLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleSelectedFile(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        reposViewModel = ViewModelProvider(this, ReposViewModelFactory(requireActivity().application))[ReposViewModel::class.java]
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]

        _binding = FragmentReposBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupAppBar(root)
        setupRecyclerView()
        setupObservers()
        setupFab()

        reposViewModel.loadDistributions()

        setupMenu()

        return root
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_repos, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_download_manager -> {
                        navigateToDownloadManager()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun handleSelectedFile(uri: android.net.Uri) {
        Snackbar.make(
            binding.root,
            "Selected file: ${uri.lastPathSegment}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout = binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_repos)
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ImageAdapter(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        val fab: FloatingActionButton = binding.fabImportRootfs
        fab.setOnClickListener {
            selectRootfsFile()
        }
    }

    private fun setupObservers() {
        reposViewModel.distributions.observe(viewLifecycleOwner) { distributions ->
            setupDistributionSpinner(distributions)
        }

        reposViewModel.architectures.observe(viewLifecycleOwner) { architectures ->
            setupArchSpinner(architectures)
        }

        reposViewModel.selectedDistribution.observe(viewLifecycleOwner) { _ ->
            val currentArchs = reposViewModel.architectures.value
            if (currentArchs != null) {
                lastArchitectures = null
                setupArchSpinner(currentArchs)
            }
        }

        reposViewModel.images.observe(viewLifecycleOwner) { images ->
            adapter.submitList(images)
            binding.textError.visibility = if (images.isEmpty() && reposViewModel.error.value == null) View.VISIBLE else View.GONE
            binding.textError.text = if (images.isEmpty()) getString(R.string.no_images_found) else ""
        }

        reposViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        reposViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = error
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.textError.visibility = View.GONE
            }
        }
    }

    private fun setupDistributionSpinner(distributions: List<String>) {
        if (distributions == lastDistributions) return
        lastDistributions = distributions

        val dropdown: MaterialAutoCompleteTextView = binding.spinnerDistribution
        dropdown.setSimpleItems(distributions.toTypedArray())

        val savedDist = reposViewModel.selectedDistribution.value
        val currentText = dropdown.text?.toString()
        val currentIndex = when {
            savedDist != null && distributions.contains(savedDist) -> distributions.indexOf(savedDist)
            !currentText.isNullOrBlank() && distributions.contains(currentText) -> distributions.indexOf(currentText)
            else -> 0
        }

        isSettingSelection = true
        dropdown.setText(distributions.getOrNull(currentIndex).orEmpty(), false)
        isSettingSelection = false

        dropdown.setOnItemClickListener { _, _, position, _ ->
            if (isSettingSelection) return@setOnItemClickListener
            val selected = distributions[position]
            val arch = binding.spinnerArch.text?.toString()
            if (arch.isNullOrBlank().not()) {
                reposViewModel.loadImages(selected, arch)
            }
        }
    }

    private fun setupArchSpinner(architectures: List<String>) {
        if (architectures == lastArchitectures) return
        lastArchitectures = architectures

        val dropdown: MaterialAutoCompleteTextView = binding.spinnerArch
        dropdown.setSimpleItems(architectures.toTypedArray())
        
        val savedArch = reposViewModel.selectedArchitecture.value
        val currentText = dropdown.text?.toString()
        val currentIndex = when {
            savedArch != null && architectures.contains(savedArch) -> architectures.indexOf(savedArch)
            !currentText.isNullOrBlank() && architectures.contains(currentText) -> architectures.indexOf(currentText)
            else -> {
                val arm64Index = architectures.indexOf("arm64")
                if (arm64Index >= 0) arm64Index else 0
            }
        }

        isSettingSelection = true
        dropdown.setText(architectures.getOrNull(currentIndex).orEmpty(), false)
        isSettingSelection = false

        dropdown.setOnItemClickListener { _, _, position, _ ->
            if (isSettingSelection) return@setOnItemClickListener
            val arch = architectures[position]
            val dist = binding.spinnerDistribution.text?.toString()
            if (!dist.isNullOrBlank()) {
                reposViewModel.filterByArchitecture(arch)
            }
        }
    }

    override fun onImageClick(image: ImageItem) {
        showDownloadConfirmDialog(image)
    }

    private fun showDownloadConfirmDialog(image: ImageItem) {
        val message = getString(
            R.string.confirm_download_message,
            image.distribution,
            image.release,
            image.architecture,
            image.variant
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_download_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_download) { _, _ ->
                startDownload(image)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDownload(image: ImageItem) {
        downloadViewModel.addDownload(
            image.distribution,
            image.release,
            image.architecture,
            image.variant,
            image.downloadUrl,
            requireContext()
        )
        Snackbar.make(
            binding.root,
            "Download started for ${image.distribution} ${image.release}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun selectRootfsFile() {
        pickContentLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("*/*"))
        )
    }

    private fun navigateToDownloadManager() {
        findNavController().navigate(R.id.navigation_download_manager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
