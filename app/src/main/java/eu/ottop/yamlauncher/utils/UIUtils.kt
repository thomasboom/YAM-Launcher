package eu.ottop.yamlauncher.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.graphics.toColorInt
import eu.ottop.yamlauncher.settings.SharedPreferenceManager

/**
 * Window styling helpers driven by user preferences.
 * View-hierarchy styling now lives in the Compose theme ([eu.ottop.yamlauncher.compose.YamTheme]);
 * only window-level operations (background, status bar) remain here.
 */
class UIUtils(private val context: Context) {

    private val sharedPreferenceManager = SharedPreferenceManager(context)

    /**
     * Sets the window background color from preferences.
     * If background is fully transparent, applies dark overlay for settings panels.
     */
    fun setBackground(window: Window, applyHomescreenDarkening: Boolean = false) {
        val bgColor = sharedPreferenceManager.getBgColor()
        val finalColor = when {
            applyHomescreenDarkening && bgColor == TRANSPARENT && sharedPreferenceManager.isHomescreenDarkeningEnabled() -> DIM_COLOR
            else -> bgColor
        }
        window.decorView.setBackgroundColor(finalColor)
    }

    /**
     * Updates status bar appearance based on text color.
     * Switches between light and dark status bar icons.
     */
    fun setStatusBarColor(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetController = window.insetsController
            // Determine if status bar should be light or dark based on text color
            when (sharedPreferenceManager.getTextString()) {
                "#FFF3F3F3" -> insetController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "#FF0C0C0C" -> insetController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "material" -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    when (currentNightMode) {
                        Configuration.UI_MODE_NIGHT_YES -> insetController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                        Configuration.UI_MODE_NIGHT_NO -> insetController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            when (sharedPreferenceManager.getTextString()) {
                "#FFF3F3F3" -> decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                "#FF0C0C0C" -> decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                "material" -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    when (currentNightMode) {
                        Configuration.UI_MODE_NIGHT_YES -> decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        Configuration.UI_MODE_NIGHT_NO -> decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
            }
        }
    }

    fun setStatusBar(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsetsController = window.insetsController
            windowInsetsController?.let {
                if (sharedPreferenceManager.isBarVisible()) {
                    it.show(WindowInsets.Type.statusBars())
                } else {
                    it.hide(WindowInsets.Type.statusBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            decorView.systemUiVisibility = if (sharedPreferenceManager.isBarVisible()) {
                decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            } else {
                decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private companion object {
        val TRANSPARENT = "#00000000".toColorInt()
        val DIM_COLOR = "#3F000000".toColorInt()
    }
}
