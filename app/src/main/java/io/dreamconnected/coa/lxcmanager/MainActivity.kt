package io.dreamconnected.coa.lxcmanager

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.topjohnwu.superuser.ipc.RootService
import io.dreamconnected.coa.lxcmanager.databinding.ActivityMainBinding
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import io.dreamconnected.coa.lxcmanager.util.ThemeUtil
import io.github.coap.ILxc
import io.github.coap.lxc.LxcManager
import io.github.coap.lxc.LxcNative

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lxcService: ILxc? = null
    private var lxcManager: LxcManager? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "LXC Service connected")
            lxcService = ILxc.Stub.asInterface(service)
            initLxcManager()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "LXC Service disconnected")
            lxcService = null
            lxcManager = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.setTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ShellCommandExecutor.initialize(this)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        when (val navView = findViewById<View>(R.id.nav_view)) {
            is BottomNavigationView -> NavigationUI.setupWithNavController(navView, navController)
            is NavigationRailView -> NavigationUI.setupWithNavController(navView, navController)
        }

        fragmentTransaction.setCustomAnimations(
            R.anim.fragment_enter,
            R.anim.fragment_exit,
            R.anim.fragment_enter_pop,
            R.anim.fragment_exit_pop
        ).commit()

        initLxcPath()
        bindLxcService()
    }

    private fun bindLxcService() {
        val intent = Intent(this, LxcNative::class.java)
        RootService.bind(intent, serviceConnection)
    }

    private fun initLxcPath() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val customLxcPath = sharedPreferences.getString("lxc_dir", "/data/share/var/lib/lxc")
        LxcNative.LXC_PATH = customLxcPath ?: LxcNative.LXC_PATH
        val sharedPref = this.getPreferences(MODE_PRIVATE)
    }

    private fun initLxcManager() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val customLxcPath = sharedPreferences.getString("lxc_dir", LxcNative.LXC_PATH)
        lxcManager = LxcManager(lxcService, customLxcPath)
        isServiceBound = true
        Log.d(TAG, "LxcManager initialized: ${lxcManager?.toString()}")
    }

    fun getLxcManager(): LxcManager? {
        return lxcManager
    }

    fun isLxcServiceAvailable(): Boolean {
        return isServiceBound && lxcManager?.isServiceAvailable == true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            RootService.unbind(serviceConnection)
            isServiceBound = false
        }
    }

    fun hideBottomNavigation() {
        binding.navView.visibility = View.GONE
    }

    fun showBottomNavigation() {
        binding.navView.visibility = View.VISIBLE
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}