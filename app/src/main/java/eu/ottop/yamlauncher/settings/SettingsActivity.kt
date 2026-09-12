package eu.ottop.yamlauncher.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import eu.ottop.yamlauncher.compose.settings.SettingsNav
import eu.ottop.yamlauncher.compose.settings.SettingsViewModel
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.PermissionUtils

/**
 * Settings activity, Jetpack Compose edition.
 * Hosts the settings navigation graph; file and permission flows live in
 * the individual settings screens and [SettingsViewModel].
 */
class SettingsActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModels()
    private val permissionUtils = PermissionUtils()
    private lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = Logger.getInstance(this)
        logger.i("SettingsActivity", "SettingsActivity started")
        enableEdgeToEdge()
        setContent {
            SettingsNav(vm = vm, onClose = ::finish)
        }
    }

    override fun onResume() {
        super.onResume()
        // Verify permissions are still granted
        if (!permissionUtils.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            vm.prefs.setContactsEnabled(false)
        }
        if (!permissionUtils.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            vm.prefs.setWeatherGPS(false)
        }
    }
}
