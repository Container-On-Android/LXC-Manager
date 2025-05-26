package io.dreamconnected.coa.lxcmanager

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.dreamconnected.coa.lxcmanager.databinding.ActivityMainBinding
import io.dreamconnected.coa.lxcmanager.util.ShellClient
import io.dreamconnected.coa.lxcmanager.util.ThemeUtil
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var shellClient = ShellClient.instance ?: throw IllegalStateException("ShellClient not initialized")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.setTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        shellClient.invokeShell(this)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        NavigationUI.setupWithNavController(bottomNavigationView, navController)

        fragmentTransaction.setCustomAnimations(
            R.anim.fragment_enter,
            R.anim.fragment_exit,
            R.anim.fragment_enter_pop,
            R.anim.fragment_exit_pop
        ).commit()
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return super.onCreateView(name, context, attrs)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val customLxcPath = sharedPreferences.getString("lxc_dir","/data/share")
        val sharedPref = this.getPreferences(MODE_PRIVATE)
        try {
            shellClient.execCommand(
                "for dir in $customLxcPath /data/share /data/lxc; do [ -d \"\$dir\" ] && echo \"\$dir\" && break; done",
                1,
                object : ShellClient.CommandOutputListener {
                    override fun onOutput(output: String?) {
                        output?.let {
                            if (it.isNotEmpty())
                                sharedPref.edit {
                                    putString(getString(R.string.lxc_path), output)
                                    putString(getString(R.string.lxc_ld_path), "$output/lib:$output/lib64:/data/sysroot/lib:/data/sysroot/lib64")
                                    putString(getString(R.string.lxc_bin_path), "$output/bin:$output/libexec/lxc:")
                                }
                        }
                    }

                    override fun onCommandComplete(code: String?) {
                    }
                }
            )
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(ShellClient.TAG, "Failed to invoke shell: ${e.message}")
        }
    }

    fun hideBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNavigationView.visibility = View.GONE
    }

    fun showBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNavigationView.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        shellClient.closeShell()
    }
}