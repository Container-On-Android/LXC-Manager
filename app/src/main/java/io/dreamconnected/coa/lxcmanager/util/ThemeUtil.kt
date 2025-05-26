package io.dreamconnected.coa.lxcmanager.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import io.dreamconnected.coa.lxcmanager.R

object ThemeUtil {
    val themeMap = mapOf(
        "AppTheme.Blue" to R.style.AppTheme_Blue,
        "AppTheme.Red" to R.style.AppTheme_Red,
        "AppTheme.Green" to R.style.AppTheme_Green,
        "AppTheme.Yellow" to R.style.AppTheme_Yellow
    )

    fun setTheme(activity: Activity?,theme: Int) {
        if (activity == null) return
        val sharedPreferences = activity.let { PreferenceManager.getDefaultSharedPreferences(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        val layoutParams = activity.window.attributes
        layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        activity.window.attributes = layoutParams
        var dynamicColors = sharedPreferences?.getBoolean("dynamicColors",false)
        if (dynamicColors == true) {
            DynamicColors.applyToActivitiesIfAvailable(activity.application)
        } else {
            setUpDarkMode(activity)
            activity.setTheme(theme)
        }
    }

    fun setTheme(activity: Activity?) {
        if (activity == null) return
        val sharedPreferences = activity.let { PreferenceManager.getDefaultSharedPreferences(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        val layoutParams = activity.window.attributes
        layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        activity.window.attributes = layoutParams
        var themeName = sharedPreferences?.getString("customColors","AppTheme.Blue")
        var dynamicColors = sharedPreferences?.getBoolean("dynamicColors",false)
        if (dynamicColors == true) {
            DynamicColors.applyToActivitiesIfAvailable(activity.application)
        } else {
            val themeResId = themeMap[themeName] ?: R.style.AppTheme
            setUpDarkMode(activity)
            activity.setTheme(themeResId)
        }
    }

    fun getTheme(context: Context?): Int {
        if (context == null) return R.style.AppTheme
        val theme = context.theme
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.theme, typedValue, true)
        return typedValue.resourceId
    }

    fun setUpDarkMode(context: Context?) {
        val sharedPreferences = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        var darkmode = sharedPreferences?.getString("theme_darkmode","SYSTEM")
        when (darkmode) {
            "ALWAYS" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            "NEVER" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}