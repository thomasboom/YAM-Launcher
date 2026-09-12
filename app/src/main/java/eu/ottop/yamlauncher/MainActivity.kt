package eu.ottop.yamlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.ottop.yamlauncher.compose.LauncherRoot
import eu.ottop.yamlauncher.compose.LauncherViewModel
import eu.ottop.yamlauncher.compose.YamTheme
import eu.ottop.yamlauncher.settings.SettingsActivity
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.tasks.BatteryReceiver
import eu.ottop.yamlauncher.utils.BiometricUtils
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.PermissionUtils
import eu.ottop.yamlauncher.utils.UIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * YAM Launcher home activity, Jetpack Compose edition.
 *
 * All UI state lives in [LauncherViewModel]; this activity only wires system
 * services (window, battery, biometric, lifecycle loops, key events).
 */
class MainActivity : FragmentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var biometricUtils: BiometricUtils
    private lateinit var uiUtils: UIUtils
    private lateinit var logger: Logger
    private val permissionUtils = PermissionUtils()
    private var batteryReceiver: BatteryReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricUtils = BiometricUtils(this)
        uiUtils = UIUtils(this)
        logger = Logger.getInstance(this)
        logger.i("MainActivity", "MainActivity started")

        // First frame already matches user config (mirrors the legacy behavior).
        val earlyPrefs = SharedPreferenceManager(this)
        if (earlyPrefs.isAutoRotationBlocked()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        uiUtils.setBackground(window, applyHomescreenDarkening = true)

        enableEdgeToEdge()
        setContent {
            val p by vm.uiPrefs.collectAsStateWithLifecycle()
            YamTheme(p = p) {
                LauncherRoot(vm = vm, onOpenSettings = ::trySettings)
            }
        }

        vm.initialLoad()
        vm.checkDefaultLauncher()

        // React to preference changes that affect the window itself.
        lifecycleScope.launch {
            vm.uiPrefs.collect { p ->
                uiUtils.setBackground(window, applyHomescreenDarkening = true)
                uiUtils.setStatusBarColor(window)
                uiUtils.setStatusBar(window)
                requestedOrientation = if (p.blockRotation) {
                    ActivityInfo.SCREEN_ORIENTATION_LOCKED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (p.batteryEnabled) registerBatteryReceiver()
                else {
                    unregisterBatteryReceiver()
                    vm.clearBatteryText()
                }
            }
        }

        // Periodic app-list refresh.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(5000)
                    vm.refreshApps()
                }
            }
        }
        // Periodic weather refresh.
        lifecycleScope.launch(Dispatchers.IO) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.updateWeather()
                    delay(vm.prefs.getWeatherUpdateIntervalMs())
                }
            }
        }
        // Periodic screen-time refresh.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.updateScreenTime()
                    delay(60_000)
                }
            }
        }
    }

    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (vm.prefs.isSettingsLocked()) {
                biometricUtils.startBiometricSettingsAuth(object : BiometricUtils.CallbackSettings {
                    override fun onAuthenticationSucceeded() {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }

                    override fun onAuthenticationFailed() {
                        logger.w("MainActivity", "Biometric authentication failed")
                    }

                    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED ->
                                logger.i("MainActivity", "Biometric authentication cancelled by user")
                            else ->
                                logger.e("MainActivity", "Biometric authentication error: $errorMessage (code: $errorCode)")
                        }
                    }
                })
            } else {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver == null) {
            try {
                batteryReceiver = BatteryReceiver.register(this, vm::setBatteryText)
            } catch (e: Exception) {
                logger.w("MainActivity", "Failed to register battery receiver: ${e.message}")
            }
        }
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                logger.w("MainActivity", "Failed to unregister battery receiver: ${e.message}")
            }
            batteryReceiver = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.closeDrawer()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!vm.drawerOpen.value && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar != 0 && !KeyEvent.isModifierKey(event.keyCode)) {
                val char = unicodeChar.toChar()
                if (char.isLetterOrDigit()) {
                    vm.openDrawerWithChar(char.toString())
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (!permissionUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            vm.prefs.setContactsEnabled(false)
        }
        if (!permissionUtils.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            vm.prefs.setWeatherGPS(false)
        }
        if (vm.returnAllowed) vm.closeDrawer()
        vm.returnAllowed = true
        vm.checkDefaultLauncher()
        vm.refreshNotifPackages()
        lifecycleScope.launch { vm.refreshApps() }
    }

    override fun onDestroy() {
        try {
            unregisterBatteryReceiver()
        } catch (e: Exception) {
            logger.w("MainActivity", "Error during onDestroy cleanup: ${e.message}")
        }
        logger.i("MainActivity", "MainActivity destroyed")
        Logger.getInstance(this).shutdown()
        super.onDestroy()
    }
}
