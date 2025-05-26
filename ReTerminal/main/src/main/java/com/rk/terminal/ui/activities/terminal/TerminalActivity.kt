package com.rk.terminal.ui.activities.terminal

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.rk.terminal.service.SessionService
import com.rk.terminal.ui.navHosts.MainActivityNavHost
import com.rk.terminal.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalActivity : ComponentActivity() {
    var sessionBinder:SessionService.SessionBinder? = null
    var isBound = false


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SessionService.SessionBinder
            sessionBinder = binder
            isBound = true

            lifecycleScope.launch(Dispatchers.Main){
                setContent {
                    KarbonTheme {
                        Surface {
                            val navController = rememberNavController()
                            MainActivityNavHost(navController = navController, mainActivity = this@TerminalActivity)
                        }
                    }
                }
            }


        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, SessionService::class.java))
        }else{
            startService(Intent(this, SessionService::class.java))
        }
        Intent(this, SessionService::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}