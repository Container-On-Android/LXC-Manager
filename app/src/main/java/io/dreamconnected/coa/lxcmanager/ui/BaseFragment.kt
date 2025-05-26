package io.dreamconnected.coa.lxcmanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.dreamconnected.coa.lxcmanager.R

abstract class BaseFragment : Fragment() {
    protected open fun setupAppBar(binding: View) {
        val collapsingToolbarLayout =
            binding.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)
        val toolbar = binding.findViewById<Toolbar>(R.id.toolbar)
        (toolbar.context as AppCompatActivity).setSupportActionBar(toolbar)
        collapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.ThemeOverlay_AppTheme_HighContrast)
        collapsingToolbarLayout.setCollapsedTitleTextAppearance(R.style.ThemeOverlay_AppTheme_MediumContrast)
    }

    abstract override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?
}
