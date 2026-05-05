package io.dreamconnected.coa.lxcmanager.ui.repos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentReposBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import io.dreamconnected.coa.lxcmanager.util.LxcTemplates
import io.dreamconnected.coa.lxcmanager.util.ScreenMask

class ReposFragment : BaseFragment(), ImageAdapter.OnImageClickListener {

    private var _binding: FragmentReposBinding? = null
    private lateinit var adapter: ImageAdapter
    private lateinit var reposViewModel: ReposViewModel

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        reposViewModel = ViewModelProvider(this)[ReposViewModel::class.java]

        _binding = FragmentReposBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupAppBar(root)
        setupRecyclerView()
        setupObservers()

        reposViewModel.loadDistributions()

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
        val fields = mutableListOf(image.distribution, image.release, image.architecture)
        if (image.variant.isNotEmpty() && image.variant != "default") {
            fields.add(image.variant)
        }
        ScreenMask(requireContext()).showTemplateSelectionDialog(
            requireContext(),
            listOf(LxcTemplates("download", fields))
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}