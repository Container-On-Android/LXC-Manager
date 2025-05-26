package io.dreamconnected.coa.lxcmanager.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.dreamconnected.coa.lxcmanager.util.ShellClient

class HomeViewModel : ViewModel() {
    var shellClient = ShellClient.instance ?: throw IllegalStateException("ShellClient not initialized")

    private val _text = MutableLiveData<String>().apply {
        val currentText = StringBuilder()
//        shellClient.execCommand("lxc-checkconfig", 5, object : ShellClient.CommandOutputListener {
//            override fun onOutput(output: String?) {
//                currentText.append(output).append("\n")
//                value = currentText.toString()
//            }
//            override fun onCommandComplete(code: String?) {
//            }
//        })
    }
    val text: LiveData<String> = _text
}