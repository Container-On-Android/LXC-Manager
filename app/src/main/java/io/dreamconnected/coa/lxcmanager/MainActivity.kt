package io.dreamconnected.coa.lxcmanager

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.dreamconnected.coa.lxcmanager.databinding.ActivityMainBinding
import io.dreamconnected.coa.lxcmanager.util.ShellCommandExecutor
import io.dreamconnected.coa.lxcmanager.util.ThemeUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.setTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ShellCommandExecutor.initialize(this)

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

        initLxcPath()
    }

    private fun initLxcPath() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val customLxcPath = sharedPreferences.getString("lxc_dir", "/data/share")
        val sharedPref = this.getPreferences(MODE_PRIVATE)

        ShellCommandExecutor.execCommand(
            "for dir in $customLxcPath /data/share /data/lxc; do [ -d \"\$dir\" ] && echo \"\$dir\" && break; done",
            object : ShellCommandExecutor.CommandOutputListener {
                override fun onOutput(output: String?) {
                    output?.let {
                        if (it.isNotEmpty()) {
                            sharedPref.edit {
                                putString(getString(R.string.lxc_path), output)
                                putString(
                                    getString(R.string.lxc_ld_path),
                                    "$output/lib:$output/lib64:/data/sysroot/lib:/data/sysroot/lib64"
                                )
                                putString(
                                    getString(R.string.lxc_bin_path),
                                    "$output/bin:$output/libexec/lxc:"
                                )
                            }
                        }
                    }
                }
                override fun onCommandComplete(code: String?) {
                }
            }
        )
    }

    fun hideBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNavigationView.visibility = View.GONE
    }

    fun showBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNavigationView.visibility = View.VISIBLE
    }
}