package io.dreamconnected.coa.lxcmanager.ui.repos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ReposViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Page not implemented"
    }
    val text: LiveData<String> = _text
}