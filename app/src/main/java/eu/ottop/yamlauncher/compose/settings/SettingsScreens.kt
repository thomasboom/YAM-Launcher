package eu.ottop.yamlauncher.compose.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.compose.AppEntry
import eu.ottop.yamlauncher.tasks.NotificationListener
import eu.ottop.yamlauncher.utils.CurboxApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object Routes {
    const val ROOT = "root"
    const val UI = "ui"
    const val HOME = "home"
    const val APP_MENU = "appmenu"
    const val CONTEXT = "context"
    const val HIDDEN = "hidden"
    const val GESTURE = "gesture/{direction}"
    const val LOCATION = "location"
    const val ABOUT = "about"

    fun gesture(direction: String) = "gesture/$direction"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNav(vm: SettingsViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val useDark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val title = when (entry?.destination?.route) {
            Routes.UI -> stringResource(R.string.ui_settings_title)
            Routes.HOME -> stringResource(R.string.home_settings_title)
            Routes.APP_MENU -> stringResource(R.string.app_settings_title)
            Routes.CONTEXT -> stringResource(R.string.context_menu_settings_title)
            Routes.HIDDEN -> stringResource(R.string.hidden_apps_title)
            Routes.GESTURE -> stringResource(R.string.select_an_app)
            Routes.LOCATION -> stringResource(R.string.find_your_city)
            Routes.ABOUT -> stringResource(R.string.about_title)
            else -> stringResource(R.string.settings_title)
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!nav.popBackStack()) onClose()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.ROOT,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable(Routes.ROOT) { RootSettingsScreen(vm, nav) }
                composable(Routes.UI) { UiSettingsScreen(vm) }
                composable(Routes.HOME) { HomeSettingsScreen(vm, nav) }
                composable(Routes.APP_MENU) { AppMenuSettingsScreen(vm, nav) }
                composable(Routes.CONTEXT) { ContextMenuSettingsScreen(vm) }
                composable(Routes.HIDDEN) { HiddenAppsScreen(vm) }
                composable(
                    Routes.GESTURE,
                    arguments = listOf(navArgument("direction") { type = NavType.StringType }),
                ) { backStack ->
                    GesturePickerScreen(vm, nav, backStack.arguments?.getString("direction").orEmpty())
                }
                composable(Routes.LOCATION) { LocationScreen(vm, nav) }
                composable(Routes.ABOUT) { AboutScreen() }
            }
        }
    }
}

// ================= helpers =================

@Composable
private fun arrayEntries(resId: Int): List<String> {
    val context = LocalContext.current
    return remember(resId) { context.resources.getStringArray(resId).toList() }
}

@Composable
private fun SettingsColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ================= root =================

@Composable
private fun RootSettingsScreen(vm: SettingsViewModel, nav: NavController) {
    val context = LocalContext.current
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    var showResetConfirm by remember { mutableStateOf(false) }

    val backupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::backupTo) }
    val restoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::restoreFrom) }
    val logFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let(vm::exportLogsTo) }

    SettingsColumn {
        remember(tick) { tick }.let {
            ActionRow(
                title = stringResource(R.string.default_home),
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (e: android.content.ActivityNotFoundException) {
                        Toast.makeText(context, context.getString(R.string.unable_to_launch_settings), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            SwitchRow(
                title = stringResource(R.string.lock_settings),
                checked = p.isSettingsLocked(),
                onCheckedChange = { p.putBoolean("lockSettings", it) },
            )
            PrefSection(stringResource(R.string.customization))
            NavRow(
                title = stringResource(R.string.ui_settings_text),
                summary = stringResource(R.string.ui_settings_summary),
                onClick = { nav.navigate(Routes.UI) },
            )
            NavRow(
                title = stringResource(R.string.home_settings_text),
                summary = stringResource(R.string.home_settings_summary),
                onClick = { nav.navigate(Routes.HOME) },
            )
            NavRow(
                title = stringResource(R.string.app_settings_text),
                summary = stringResource(R.string.app_settings_summary),
                onClick = { nav.navigate(Routes.APP_MENU) },
            )
            PrefSection(stringResource(R.string.hidden_apps_title))
            NavRow(
                title = stringResource(R.string.hidden_apps_text),
                summary = stringResource(R.string.hidden_apps_summary),
                onClick = { nav.navigate(Routes.HIDDEN) },
            )
            PrefSection(stringResource(R.string.backup_restore))
            ActionRow(
                title = stringResource(R.string.backup),
                summary = stringResource(R.string.backup_summary),
                onClick = {
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    backupFile.launch("yamlauncher_backup_$ts.json")
                },
            )
            ActionRow(
                title = stringResource(R.string.restore),
                summary = stringResource(R.string.restore_summary),
                onClick = { restoreFile.launch(arrayOf("application/json")) },
            )
            PrefSection(stringResource(R.string.debug_logs))
            ActionRow(
                title = stringResource(R.string.export_logs),
                summary = stringResource(R.string.export_logs_summary),
                onClick = {
                    if (!vm.hasLogs()) {
                        Toast.makeText(context, context.getString(R.string.no_logs_to_export), Toast.LENGTH_SHORT).show()
                        return@ActionRow
                    }
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    logFile.launch("yam_launcher_logs_$ts.txt")
                },
            )
            ActionRow(
                title = stringResource(R.string.clear_logs),
                summary = stringResource(R.string.clear_logs_summary),
                onClick = vm::clearLogs,
            )
            PrefSection(stringResource(R.string.about))
            NavRow(title = stringResource(R.string.about_title), onClick = { nav.navigate(Routes.ABOUT) })
            PrefSection(stringResource(R.string.reset))
            ActionRow(title = stringResource(R.string.restart_text), onClick = vm::restart)
            ActionRow(title = stringResource(R.string.reset_text), onClick = { showResetConfirm = true })
        }
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_title),
            message = stringResource(R.string.reset_confirm_text),
            onConfirm = {
                showResetConfirm = false
                vm.resetAll()
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

// ================= UI =================

@Composable
private fun UiSettingsScreen(vm: SettingsViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    SettingsColumn {
        remember(tick) { tick }.let {
            PrefSection(stringResource(R.string.appearance))
            ListRow(
                title = stringResource(R.string.background_color),
                entries = arrayEntries(R.array.bg_options),
                values = arrayEntries(R.array.bg_values),
                current = p.getBgColorString(),
                onSelect = { p.putString("bgColor", it) },
            )
            ListRow(
                title = stringResource(R.string.text_color),
                entries = arrayEntries(R.array.color_options),
                values = arrayEntries(R.array.color_values),
                current = p.getTextString(),
                onSelect = { p.putString("textColor", it) },
            )
            ListRow(
                title = stringResource(R.string.text_font),
                entries = arrayEntries(R.array.font_options),
                values = arrayEntries(R.array.font_values),
                current = p.getTextFont(),
                onSelect = { p.putString("textFont", it) },
            )
            ListRow(
                title = stringResource(R.string.text_style),
                entries = arrayEntries(R.array.style_options),
                values = arrayEntries(R.array.style_values),
                current = p.getTextStyle(),
                onSelect = { p.putString("textStyle", it) },
            )
            SwitchRow(
                title = stringResource(R.string.text_shadow),
                checked = p.isTextShadowEnabled(),
                onCheckedChange = { p.putBoolean("textShadow", it) },
            )
            PrefSection(stringResource(R.string.operation))
            ListRow(
                title = stringResource(R.string.animation_speed),
                entries = arrayEntries(R.array.animation_options),
                values = arrayEntries(R.array.animation_values),
                current = p.getAnimationSpeedString(),
                onSelect = { p.putString("animationSpeed", it) },
            )
            ListRow(
                title = stringResource(R.string.swipe_threshold),
                entries = arrayEntries(R.array.animation_options),
                values = arrayEntries(R.array.swipe_values),
                current = p.getSwipeThresholdString(),
                onSelect = { p.putString("swipeThreshold", it) },
            )
            ListRow(
                title = stringResource(R.string.swipe_velocity_threshold),
                entries = arrayEntries(R.array.animation_options),
                values = arrayEntries(R.array.swipe_values),
                current = p.getSwipeVelocityString(),
                onSelect = { p.putString("swipeVelocity", it) },
            )
            SwitchRow(
                title = stringResource(R.string.homescreen_darkening),
                checked = p.isHomescreenDarkeningEnabled(),
                onCheckedChange = { p.putBoolean("homescreenDarkening", it) },
            )
            SwitchRow(
                title = stringResource(R.string.app_drawer_darkening),
                checked = p.isAppDrawerDarkeningEnabled(),
                onCheckedChange = { p.putBoolean("appDrawerDarkening", it) },
            )
            SwitchRow(
                title = stringResource(R.string.block_auto_rotation),
                summary = stringResource(R.string.block_auto_rotation_summary),
                checked = p.isAutoRotationBlocked(),
                onCheckedChange = { p.putBoolean("blockAutoRotation", it) },
            )
            SwitchRow(
                title = stringResource(R.string.show_status_bar),
                checked = p.isBarVisible(),
                onCheckedChange = { p.putBoolean("barVisibility", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_confirmation),
                summary = stringResource(R.string.launch_confirmation_text),
                checked = p.isConfirmationEnabled(),
                onCheckedChange = { p.putBoolean("enableConfirmation", it) },
            )
        }
    }
}

// ================= home =================

@Composable
private fun HomeSettingsScreen(vm: SettingsViewModel, nav: NavController) {
    val context = LocalContext.current
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> p.setWeatherGPS(granted) }
    val curboxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setScreenTimeEnabled(context, result.resultCode == Activity.RESULT_OK)
    }

    SettingsColumn {
        remember(tick) { tick }.let {
            PrefSection(stringResource(R.string.clock))
            SwitchRow(
                title = stringResource(R.string.show_clock),
                checked = p.isClockEnabled(),
                onCheckedChange = { p.putBoolean("clockEnabled", it) },
            )
            ListRow(
                title = stringResource(R.string.clock_alignment),
                entries = arrayEntries(R.array.h_alignment_options),
                values = arrayEntries(R.array.h_alignment_values),
                current = p.getClockAlignment(),
                enabled = p.isClockEnabled(),
                onSelect = { p.putString("clockAlignment", it) },
            )
            ListRow(
                title = stringResource(R.string.clock_size),
                entries = arrayEntries(R.array.size_options),
                values = arrayEntries(R.array.size_values),
                current = p.getClockSize(),
                enabled = p.isClockEnabled(),
                onSelect = { p.putString("clockSize", it) },
            )
            SwitchRow(
                title = stringResource(R.string.clicking_time_opens_clock),
                checked = p.isClockGestureEnabled(),
                enabled = p.isClockEnabled(),
                onCheckedChange = { p.putBoolean("clockClick", it) },
            )
            SwitchRow(
                title = stringResource(R.string.custom_clock_gesture),
                checked = p.isGestureEnabled("clock"),
                enabled = p.isClockEnabled() && p.isClockGestureEnabled(),
                onCheckedChange = { p.putBoolean("clockSwipe", it) },
            )
            NavRow(
                title = stringResource(R.string.set_clock_app),
                summary = p.getGestureName("clock"),
                enabled = p.isClockEnabled() && p.isGestureEnabled("clock"),
                onClick = { nav.navigate(Routes.gesture("clock")) },
            )

            PrefSection(stringResource(R.string.date))
            SwitchRow(
                title = stringResource(R.string.show_date),
                checked = p.isDateEnabled(),
                onCheckedChange = { p.putBoolean("dateEnabled", it) },
            )
            ListRow(
                title = stringResource(R.string.date_size),
                entries = arrayEntries(R.array.size_options),
                values = arrayEntries(R.array.size_values),
                current = p.getDateSize(),
                enabled = p.isDateEnabled(),
                onSelect = { p.putString("dateSize", it) },
            )
            SwitchRow(
                title = stringResource(R.string.battery_indicator),
                checked = p.isBatteryEnabled(),
                enabled = p.isDateEnabled(),
                onCheckedChange = { p.putBoolean("batteryEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.clicking_date_opens_calendar),
                checked = p.isDateGestureEnabled(),
                enabled = p.isDateEnabled(),
                onCheckedChange = { p.putBoolean("dateClick", it) },
            )
            SwitchRow(
                title = stringResource(R.string.custom_date_gesture),
                checked = p.isGestureEnabled("date"),
                enabled = p.isDateEnabled() && p.isDateGestureEnabled(),
                onCheckedChange = { p.putBoolean("dateSwipe", it) },
            )
            NavRow(
                title = stringResource(R.string.set_calendar_app),
                summary = p.getGestureName("date"),
                enabled = p.isDateEnabled() && p.isGestureEnabled("date"),
                onClick = { nav.navigate(Routes.gesture("date")) },
            )

            PrefSection(stringResource(R.string.screen_time))
            SwitchRow(
                title = stringResource(R.string.show_screen_time),
                summary = stringResource(R.string.screen_time_summary),
                checked = p.isScreenTimeEnabled(),
                enabled = p.isDateEnabled(),
                onCheckedChange = { enable ->
                    if (!enable) {
                        setScreenTimeEnabled(context, false)
                    } else {
                        val intent = CurboxApiClient.createPermissionIntent(context)
                        if (intent == null) {
                            Toast.makeText(context, context.getString(R.string.curbox_not_installed), Toast.LENGTH_SHORT).show()
                        } else {
                            curboxLauncher.launch(intent)
                        }
                    }
                },
            )

            PrefSection(stringResource(R.string.weather))
            SwitchRow(
                title = stringResource(R.string.weather),
                checked = p.isWeatherEnabled(),
                enabled = p.isDateEnabled(),
                onCheckedChange = { p.putBoolean("weatherEnabled", it) },
            )
            NavRow(
                title = stringResource(R.string.set_weather_app),
                summary = p.getGestureName("weather"),
                enabled = p.isDateEnabled() && p.isWeatherEnabled(),
                onClick = { nav.navigate(Routes.gesture("weather")) },
            )
            SwitchRow(
                title = stringResource(R.string.gps_location),
                checked = p.isWeatherGPS(),
                enabled = p.isDateEnabled() && p.isWeatherEnabled(),
                onCheckedChange = { enable ->
                    if (enable && !hasLocationPermission(context)) {
                        locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    } else {
                        p.setWeatherGPS(enable)
                    }
                },
            )
            NavRow(
                title = stringResource(R.string.set_manual_location),
                summary = p.getWeatherRegion().ifEmpty { null },
                enabled = p.isDateEnabled() && p.isWeatherEnabled() && !p.isWeatherGPS(),
                onClick = { nav.navigate(Routes.LOCATION) },
            )
            ListRow(
                title = stringResource(R.string.units),
                entries = arrayEntries(R.array.temp_units),
                values = arrayEntries(R.array.unit_values),
                current = p.getTempUnits(),
                enabled = p.isDateEnabled() && p.isWeatherEnabled(),
                onSelect = { p.putString("tempUnits", it) },
            )
            EditRow(
                title = stringResource(R.string.weather_update_interval),
                summary = stringResource(R.string.weather_update_interval_summary),
                value = p.getWeatherUpdateIntervalRaw(),
                enabled = p.isDateEnabled() && p.isWeatherEnabled(),
                onSave = { p.putString("weatherUpdateInterval", it) },
            )

            PrefSection(stringResource(R.string.shortcuts))
            ListRow(
                title = stringResource(R.string.number_of_shortcuts),
                entries = arrayEntries(R.array.shortcut_options),
                values = arrayEntries(R.array.shortcut_options),
                current = p.getShortcutNumber().toString(),
                onSelect = { p.putString("shortcutNo", it) },
            )
            ListRow(
                title = stringResource(R.string.horizontal_alignment),
                entries = arrayEntries(R.array.h_alignment_options),
                values = arrayEntries(R.array.h_alignment_values),
                current = p.getShortcutAlignment(),
                onSelect = { p.putString("shortcutAlignment", it) },
            )
            ListRow(
                title = stringResource(R.string.vertical_alignment),
                entries = arrayEntries(R.array.v_alignment_options),
                values = arrayEntries(R.array.v_alignment_values),
                current = p.getShortcutVAlignment(),
                onSelect = { p.putString("shortcutVAlignment", it) },
            )
            ListRow(
                title = stringResource(R.string.shortcut_size),
                entries = arrayEntries(R.array.size_options),
                values = arrayEntries(R.array.size_values),
                current = p.getShortcutSize(),
                onSelect = { p.putString("shortcutSize", it) },
            )
            ListRow(
                title = stringResource(R.string.shortcut_spacing),
                entries = arrayEntries(R.array.shortcut_spacing_options),
                values = arrayEntries(R.array.shortcut_spacing_values),
                current = p.getShortcutWeight().toString(),
                onSelect = { p.putString("shortcutWeight", it) },
            )
            SwitchRow(
                title = stringResource(R.string.lock_shortcuts),
                checked = p.areShortcutsLocked(),
                onCheckedChange = { p.putBoolean("lockShortcuts", it) },
            )
            SwitchRow(
                title = stringResource(R.string.hidden_shortcut_title),
                summary = stringResource(R.string.hidden_shortcuts),
                checked = p.showHiddenShortcuts(),
                onCheckedChange = { p.putBoolean("showHiddenShortcuts", it) },
            )
            SwitchRow(
                title = stringResource(R.string.notification_dots),
                summary = stringResource(R.string.notification_dots_summary),
                checked = p.isNotificationDotsEnabled(),
                onCheckedChange = { enable ->
                    if (enable && !NotificationListener.isEnabled(context)) {
                        NotificationListener.requestPermission(context)
                    } else {
                        p.putBoolean("notificationDots", enable)
                    }
                },
            )

            PrefSection(stringResource(R.string.gestures))
            SwitchRow(
                title = stringResource(R.string.swipe_left),
                checked = p.isGestureEnabled("left"),
                onCheckedChange = { p.putBoolean("leftSwipe", it) },
            )
            NavRow(
                title = stringResource(R.string.left_swipe_app),
                summary = p.getGestureName("left"),
                enabled = p.isGestureEnabled("left"),
                onClick = { nav.navigate(Routes.gesture("left")) },
            )
            SwitchRow(
                title = stringResource(R.string.swipe_right),
                checked = p.isGestureEnabled("right"),
                onCheckedChange = { p.putBoolean("rightSwipe", it) },
            )
            NavRow(
                title = stringResource(R.string.right_swipe_app),
                summary = p.getGestureName("right"),
                enabled = p.isGestureEnabled("right"),
                onClick = { nav.navigate(Routes.gesture("right")) },
            )
            SwitchRow(
                title = stringResource(R.string.double_tap),
                checked = p.isDoubleTapEnabled(),
                onCheckedChange = { p.putBoolean("doubleTap", it) },
            )
            ListRow(
                title = stringResource(R.string.double_tap_action),
                entries = arrayEntries(R.array.double_tap_action_options),
                values = arrayEntries(R.array.double_tap_action_values),
                current = p.getDoubleTapAction(),
                enabled = p.isDoubleTapEnabled(),
                onSelect = { p.putString("doubleTapAction", it) },
            )
            NavRow(
                title = stringResource(R.string.double_tap_app),
                summary = p.getGestureName("doubleTap"),
                enabled = p.isDoubleTapEnabled() && p.getDoubleTapAction() == "app",
                onClick = { nav.navigate(Routes.gesture("doubleTap")) },
            )
        }
    }
}

private fun setScreenTimeEnabled(context: android.content.Context, enabled: Boolean) {
    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        .edit { putBoolean("screenTimeEnabled", enabled) }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

// ================= app menu =================

@Composable
private fun AppMenuSettingsScreen(vm: SettingsViewModel, nav: NavController) {
    val context = LocalContext.current
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> p.setContactsEnabled(granted) }

    SettingsColumn {
        remember(tick) { tick }.let {
            PrefSection(stringResource(R.string.apps))
            ListRow(
                title = stringResource(R.string.horizontal_alignment),
                entries = arrayEntries(R.array.h_alignment_options),
                values = arrayEntries(R.array.h_alignment_values),
                current = p.getAppAlignment(),
                onSelect = { p.putString("appMenuAlignment", it) },
            )
            ListRow(
                title = stringResource(R.string.app_size),
                entries = arrayEntries(R.array.size_options),
                values = arrayEntries(R.array.size_values),
                current = p.getAppSize(),
                onSelect = { p.putString("appMenuSize", it) },
            )
            ListRow(
                title = stringResource(R.string.app_spacing),
                entries = arrayEntries(R.array.app_spacing_options),
                values = arrayEntries(R.array.app_spacing_values),
                current = p.getAppSpacing().toString(),
                onSelect = { p.putString("appSpacing", it) },
            )
            SwitchRow(
                title = stringResource(R.string.app_strict_click),
                summary = stringResource(R.string.app_strict_click_summary),
                checked = p.isAppStrictClickEnabled(),
                onCheckedChange = { p.putBoolean("appStrictClick", it) },
            )
            SwitchRow(
                title = stringResource(R.string.alphabet_index_enabled),
                summary = stringResource(R.string.alphabet_index_summary),
                checked = p.isAlphabetIndexEnabled(),
                onCheckedChange = { p.putBoolean("alphabetIndexEnabled", it) },
            )
            ListRow(
                title = stringResource(R.string.alphabet_index_position),
                entries = arrayEntries(R.array.alphabet_index_position_options),
                values = arrayEntries(R.array.alphabet_index_position_values),
                current = p.getAlphabetIndexPosition(),
                enabled = p.isAlphabetIndexEnabled(),
                onSelect = { p.putString("alphabetIndexPosition", it) },
            )
            SwitchRow(
                title = stringResource(R.string.contacts_menu),
                checked = p.areContactsEnabled(),
                onCheckedChange = { enable ->
                    if (enable && !hasContactsPermission(context)) {
                        contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                    } else {
                        p.setContactsEnabled(enable)
                    }
                },
            )
            val webSearchOn = rawBoolean(context, "webSearchEnabled", false)
            val autoLaunchOn = p.isAutoLaunchEnabled()
            SwitchRow(
                title = stringResource(R.string.internet_search),
                summary = if (p.isSearchEnabled() && autoLaunchOn) {
                    stringResource(R.string.web_search_disabled_reason_auto_open)
                } else null,
                checked = webSearchOn,
                enabled = p.isSearchEnabled() && !autoLaunchOn,
                onCheckedChange = {
                    if (it && autoLaunchOn) p.putBoolean("autoLaunch", false)
                    p.putBoolean("webSearchEnabled", it)
                },
            )
            NavRow(
                title = stringResource(R.string.context_menu_settings_text),
                summary = stringResource(R.string.context_menu_settings_summary),
                onClick = { nav.navigate(Routes.CONTEXT) },
            )
            PrefSection(stringResource(R.string.search_text))
            SwitchRow(
                title = stringResource(R.string.enable_search),
                checked = p.isSearchEnabled(),
                onCheckedChange = { p.putBoolean("searchEnabled", it) },
            )
            ListRow(
                title = stringResource(R.string.search_alignment),
                entries = arrayEntries(R.array.h_alignment_options),
                values = arrayEntries(R.array.h_alignment_values),
                current = p.getSearchAlignment(),
                enabled = p.isSearchEnabled(),
                onSelect = { p.putString("searchAlignment", it) },
            )
            ListRow(
                title = stringResource(R.string.search_size),
                entries = arrayEntries(R.array.size_options),
                values = arrayEntries(R.array.size_values),
                current = p.getSearchSize(),
                enabled = p.isSearchEnabled(),
                onSelect = { p.putString("searchSize", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_fuzzy_search),
                checked = p.isFuzzySearchEnabled(),
                enabled = p.isSearchEnabled(),
                onCheckedChange = { p.putBoolean("fuzzySearchEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.automatically_open_keyboard),
                checked = p.isAutoKeyboardEnabled(),
                enabled = p.isSearchEnabled(),
                onCheckedChange = { p.putBoolean("autoKeyboard", it) },
            )
            SwitchRow(
                title = stringResource(R.string.automatic_app_opening),
                summary = if (webSearchOn) {
                    stringResource(R.string.auto_launch_disabled_reason_web_search)
                } else {
                    stringResource(R.string.auto_launch_summary)
                },
                checked = autoLaunchOn,
                enabled = p.isSearchEnabled() && !webSearchOn,
                onCheckedChange = {
                    if (it && webSearchOn) p.putBoolean("webSearchEnabled", false)
                    p.putBoolean("autoLaunch", it)
                },
            )
        }
    }
}

private fun rawBoolean(context: android.content.Context, key: String, default: Boolean): Boolean =
    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(key, default)

private fun hasContactsPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.READ_CONTACTS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

// ================= context menu =================

@Composable
private fun ContextMenuSettingsScreen(vm: SettingsViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    SettingsColumn {
        remember(tick) { tick }.let {
            SwitchRow(
                title = stringResource(R.string.enable_pin),
                checked = p.isPinEnabled(),
                onCheckedChange = { p.putBoolean("pinEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_info),
                checked = p.isInfoEnabled(),
                onCheckedChange = { p.putBoolean("infoEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_uninstall),
                checked = p.isUninstallEnabled(),
                onCheckedChange = { p.putBoolean("uninstallEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_rename),
                checked = p.isRenameEnabled(),
                onCheckedChange = { p.putBoolean("renameEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_hide),
                checked = p.isHideEnabled(),
                onCheckedChange = { p.putBoolean("hideEnabled", it) },
            )
            SwitchRow(
                title = stringResource(R.string.enable_close),
                checked = p.isCloseEnabled(),
                onCheckedChange = { p.putBoolean("closeEnabled", it) },
            )
        }
    }
}

// ================= hidden apps =================

@Composable
private fun HiddenAppsScreen(vm: SettingsViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(tick) {
        all = vm.hiddenApps()
    }
    LaunchedEffect(Unit) {
        if (p.isAutoKeyboardEnabled()) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val filtered = remember(all, query, tick) {
        filterEntries(vm, all, query, p.isFuzzySearchEnabled())
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.componentString + "#" + it.profile }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirm = entry }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = vm.cachedName(entry),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    confirm?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_title),
            message = "${stringResource(R.string.hidden_confirm_text)} ${vm.cachedName(entry)}?",
            onConfirm = {
                scope.launch {
                    p.setAppVisible(entry.componentString, entry.profile)
                    confirm = null
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun filterEntries(
    vm: SettingsViewModel,
    apps: List<AppEntry>,
    rawQuery: String,
    fuzzy: Boolean,
): List<AppEntry> {
    val cleanQuery = vm.stringUtils.cleanString(rawQuery) ?: return apps
    if (cleanQuery.isEmpty()) return apps
    val queryLower = cleanQuery.lowercase()
    val fuzzyPattern = if (fuzzy) vm.stringUtils.getFuzzyPattern(cleanQuery) else null
    val exact = mutableListOf<AppEntry>()
    val prefix = mutableListOf<AppEntry>()
    val other = mutableListOf<AppEntry>()
    for (entry in apps) {
        val cleaned = vm.stringUtils.cleanString(vm.cachedName(entry)).orEmpty()
        if (cleaned.isEmpty()) continue
        val lower = cleaned.lowercase()
        if (!lower.contains(queryLower) && fuzzyPattern?.containsMatchIn(cleaned) != true) continue
        when {
            lower == queryLower -> exact.add(entry)
            lower.startsWith(queryLower) -> prefix.add(entry)
            else -> other.add(entry)
        }
    }
    return exact + prefix + other
}

// ================= gesture picker =================

@Composable
private fun GesturePickerScreen(vm: SettingsViewModel, nav: NavController, direction: String) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val p = vm.prefs
    var all by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        all = vm.installedApps(includeHidden = true)
        if (p.isAutoKeyboardEnabled()) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val filtered = remember(all, query, tick) {
        filterEntries(vm, all, query, p.isFuzzySearchEnabled())
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.componentString + "#" + it.profile }) { entry ->
                Text(
                    text = vm.cachedName(entry),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirm = entry }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
    confirm?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_title),
            message = "${stringResource(R.string.app_confirm_text)} ${vm.cachedName(entry)}?",
            onConfirm = {
                val name = vm.cachedName(entry)
                p.setGestures(
                    direction,
                    "$name§splitter§${entry.componentString}§splitter§${entry.profile}",
                )
                nav.popBackStack()
            },
            onDismiss = { confirm = null },
        )
    }
}

// ================= location =================

@Composable
private fun LocationScreen(vm: SettingsViewModel, nav: NavController) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var confirm by remember { mutableStateOf<Map<String, String>?>(null) }
    var seq by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(query) {
        val current = ++seq
        delay(350)
        if (current != seq) return@LaunchedEffect
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.IO) { vm.weatherSystem.getSearchedLocations(query) }
        if (current == seq) results = found
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { (it["latitude"] ?: "") + "," + (it["longitude"] ?: "") + (it["name"] ?: "") }) { loc ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirm = loc }
                        .padding(horizontal = 35.dp, vertical = 14.dp),
                ) {
                    Text(text = loc["name"].orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    val region = listOf(loc["region"].orEmpty(), loc["country"].orEmpty())
                        .filter { it.isNotEmpty() }.joinToString(", ")
                    if (region.isNotEmpty()) {
                        Text(text = region, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        HtmlLinkText(
            html = stringResource(R.string.location_link),
            modifier = Modifier.padding(15.dp),
        )
    }
    confirm?.let { loc ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_title),
            message = "${stringResource(R.string.app_confirm_text)} ${loc["name"].orEmpty()}?",
            onConfirm = {
                vm.prefs.setWeatherLocation(
                    "latitude=${loc["latitude"]}&longitude=${loc["longitude"]}",
                    loc["name"],
                )
                nav.popBackStack()
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun HtmlLinkText(html: String, modifier: Modifier = Modifier) {
    val annotated = remember(html) {
        buildAnnotatedString {
            val linkStyle = TextLinkStyles(
                style = SpanStyle(
                    color = androidx.compose.ui.graphics.Color(0xFF7F80CBC4),
                    textDecoration = TextDecoration.Underline,
                )
            )
            val stripped = html.replace("<br>", "\n").replace("\\n", "\n")
            val parts = Regex("(<a href=\"[^\"]+\">[^<]+</a>)").split(stripped)
            val linkMatches = Regex("<a href=\"([^\"]+)\">([^<]+)</a>").findAll(stripped).toList()
            var linkIdx = 0
            for ((i, part) in parts.withIndex()) {
                if (i % 2 == 0) {
                    append(part.replace(Regex("<[^>]+>"), ""))
                } else {
                    val m = linkMatches[linkIdx++]
                    val start = length
                    append(m.groupValues[2])
                    addLink(LinkAnnotation.Url(m.groupValues[1], linkStyle), start, length)
                }
            }
            if (length == 0 && stripped.isNotBlank()) append(stripped)
        }
    }
    Text(text = annotated, modifier = modifier, style = MaterialTheme.typography.bodySmall)
}

private fun donateLink(label: String, url: String): String =
    "<a href=\"$url\">$label</a>"

// ================= about =================

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val version = remember {
        try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            "v" + info.versionName
        } catch (e: Exception) {
            "v?.?"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.creditName),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        HtmlLinkText(html = stringResource(R.string.github_link))
        Spacer(Modifier.height(16.dp))
        HtmlLinkText(html = stringResource(R.string.fdroid_link))
        Spacer(Modifier.height(16.dp))
        HtmlLinkText(html = stringResource(R.string.izzy_link))
        Spacer(Modifier.height(16.dp))
        HtmlLinkText(html = stringResource(R.string.play_link))
        Spacer(Modifier.height(16.dp))
        HtmlLinkText(html = donateLink(stringResource(R.string.donate), stringResource(R.string.ko_fi_link)))
        Spacer(Modifier.weight(1f))
        Text(text = version, style = MaterialTheme.typography.bodyMedium)
    }
}
