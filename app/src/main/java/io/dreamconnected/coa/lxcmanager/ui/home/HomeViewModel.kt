package io.dreamconnected.coa.lxcmanager.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        val currentText = StringBuilder()
    }
    val text: LiveData<String> = _text
}