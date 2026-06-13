package io.dreamconnected.coa.lxcmanager.ui.download

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentDownloadManagerBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment

class DownloadManagerFragment : BaseFragment(), DownloadAdapter.DownloadItemClickListener {

    private var _binding: FragmentDownloadManagerBinding? = null
    private lateinit var adapter: DownloadAdapter
    private lateinit var downloadViewModel: DownloadViewModel

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]

        _binding = FragmentDownloadManagerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        downloadViewModel.initPrefs(requireContext())
        setupAppBar(root)
        setupRecyclerView()
        setupObservers()

        downloadViewModel.loadDownloads()

        return root
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout = binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_download_manager)
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = DownloadAdapter(this)
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        downloadViewModel.downloadItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onItemClick(item: DownloadItem) {
        when (item.status) {
            DownloadStatus.COMPLETED -> {
                showCompletedDialog(item)
            }
            DownloadStatus.FAILED -> {
                showRetryDialog(item)
            }
            DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> {
                showInterruptedDialog(item)
            }
            DownloadStatus.PAUSED -> {
                showPausedDialog(item)
            }
        }
    }

    private fun showInterruptedDialog(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Download Interrupted")
            .setMessage("The download was interrupted. What would you like to do?")
            .setPositiveButton("Resume Download") { _, _ ->
                downloadViewModel.retryDownload(item.id, requireContext())
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteDownload(item)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showPausedDialog(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Download Paused")
            .setMessage("The download is paused. What would you like to do?")
            .setPositiveButton("Resume Download") { _, _ ->
                downloadViewModel.retryDownload(item.id, requireContext())
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteDownload(item)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showCompletedDialog(item: DownloadItem) {
        val options = arrayOf("Use to create container", "Delete")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${item.distribution} ${item.release}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createContainerFromDownload(item)
                    1 -> deleteDownload(item)
                }
            }
            .show()
    }

    private fun showRetryDialog(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Download Failed")
            .setMessage("Do you want to retry downloading?")
            .setPositiveButton("Retry") { _, _ ->
                downloadViewModel.retryDownload(item.id, requireContext())
            }
            .setNeutralButton("Delete") { _, _ ->
                deleteDownload(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createContainerFromDownload(item: DownloadItem) {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "Enter container name"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Container")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val containerName = editText.text?.toString()?.trim()
                
                if (!containerName.isNullOrEmpty()) {
                    installContainer(item, containerName)
                } else {
                    android.widget.Toast.makeText(requireContext(), "Container name cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installContainer(item: DownloadItem, containerName: String) {
        val context = requireContext()
        val progressBar = ProgressBar(context)
        val padding = context.resources.displayMetrics.density.toInt() * 24
        progressBar.setPadding(padding, padding, padding, padding)
        progressBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        val progressDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Installing Container")
            .setMessage("Extracting files and creating container...")
            .setView(progressBar)
            .setCancelable(false)
            .create()

        progressDialog.show()

        downloadViewModel.installContainer(item, containerName, requireContext()) { success, message ->
            progressDialog.dismiss()
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (success) "Success" else "Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    if (success) {
                        navigateToDashboard()
                    }
                }
                .show()
        }
    }

    private fun navigateToDashboard() {
        val navController = findNavController()
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.navigation_download_manager, true)
            .build()
        navController.navigate(R.id.navigation_dashboard, null, navOptions)
    }

    private fun deleteDownload(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Download")
            .setMessage("Are you sure you want to delete this download?")
            .setPositiveButton("Delete") { _, _ ->
                downloadViewModel.deleteDownload(item, requireContext())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}