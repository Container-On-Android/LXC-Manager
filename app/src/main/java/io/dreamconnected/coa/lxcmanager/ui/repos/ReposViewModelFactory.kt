package io.dreamconnected.coa.lxcmanager.ui.repos

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ReposViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReposViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReposViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
