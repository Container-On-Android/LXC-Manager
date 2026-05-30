package io.dreamconnected.coa.lxcmanager.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.dreamconnected.coa.lxcmanager.R
import io.dreamconnected.coa.lxcmanager.databinding.FragmentSettingsBinding
import io.dreamconnected.coa.lxcmanager.ui.BaseFragment
import rikka.material.app.DayNightDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import rikka.material.app.DayNightDelegate.MODE_NIGHT_NO
import rikka.material.app.DayNightDelegate.MODE_NIGHT_YES
import rikka.material.app.DayNightDelegate.setDefaultNightMode
import rikka.preference.SimpleMenuPreference
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setupPreferenceListeners()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isDynamicEnabled = sharedPreferences.getBoolean("dynamicColors", false)
            findPreference<SimpleMenuPreference>("customColors")?.isVisible = !isDynamicEnabled
        }

        private fun setupPreferenceListeners() {
            findPreference<rikka.material.preference.MaterialSwitchPreference>("dynamicColors")?.setOnPreferenceChangeListener { _, newValue ->
                val enable = newValue as Boolean
                findPreference<SimpleMenuPreference>("customColors")?.isVisible = !enable
                requireActivity().recreate()
                true
            }

            findPreference<SimpleMenuPreference>("customColors")?.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }

            findPreference<SimpleMenuPreference>("theme_darkmode")?.setOnPreferenceChangeListener { _, newValue ->
                val darkmode = newValue as String
                when (darkmode) {
                    "ALWAYS" -> setDefaultNightMode(MODE_NIGHT_YES)
                    "NEVER" -> setDefaultNightMode(MODE_NIGHT_NO)
                    else -> setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
                }
                requireActivity().recreate()
                true
            }

            findPreference<Preference>("lxc_dir")?.setOnPreferenceClickListener {
                showLxcDirDialog()
                true
            }
        }

        private fun showLxcDirDialog() {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val currentPath = sharedPreferences.getString("lxc_dir", "/data/share/var/lib/lxc") ?: "/data/share/var/lib/lxc"

            val styledContext = ContextThemeWrapper(
                requireContext(),
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox
            )

            val editText = TextInputEditText(styledContext).apply {
                setText(currentPath)
                hint = "/data/share/var/lib/lxc"
            }

            val textInputLayout = TextInputLayout(styledContext).apply {
                setPadding(48, 32, 48, 32)
                addView(editText)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_settings_lxc_dir)
                .setView(textInputLayout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newPath = editText.text.toString().ifBlank { "/data/share/var/lib/lxc" }
                    sharedPreferences.edit { putString("lxc_dir", newPath) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}