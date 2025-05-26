package io.dreamconnected.coa.lxcmanager.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentSettingsBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment

open class SettingsFragment : BaseFragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (savedInstanceState == null) {
            val preferenceFragment = PreferenceFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.container_settings, preferenceFragment)
                .commit()
        }

        setupAppBar(root)

        return root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().theme.applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)
    }

    override fun setupAppBar(binding: View) {
        super.setupAppBar(binding)
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        collapsingToolbarLayout.title = getString(R.string.title_settings)
    }

    class PreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings_preference)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}