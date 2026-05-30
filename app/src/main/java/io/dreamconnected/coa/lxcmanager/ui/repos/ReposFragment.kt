package io.dreamconnected.coa.lxcmanager.ui.repos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentReposBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.ui.download.DownloadViewModel

class ReposFragment : BaseFragment(), ImageAdapter.OnImageClickListener {

    private var _binding: FragmentReposBinding? = null
    private lateinit var adapter: ImageAdapter
    private lateinit var reposViewModel: ReposViewModel
    private lateinit var downloadViewModel: DownloadViewModel

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        reposViewModel = ViewModelProvider(this)[ReposViewModel::class.java]
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]

        _binding = FragmentReposBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupAppBar(root)
        setupRecyclerView()
        setupObservers()
        setupFab()

        reposViewModel.loadDistributions()

        setHasOptionsMenu(true)

        return root
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
        val spinner = binding.spinnerDistribution
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, distributions)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = distributions[position]
                reposViewModel.loadImages(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_SELECT_ROOTFS)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_repos, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_download_manager -> {
                navigateToDownloadManager()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToDownloadManager() {
        findNavController().navigate(R.id.navigation_download_manager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_SELECT_ROOTFS = 1001
    }
}