package eu.ottop.yamlauncher.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.Logger

// Extension functions for safe typed access to SharedPreferences
private inline fun SharedPreferences.getStringAsInt(key: String, default: Int): Int {
    return getString(key, default.toString())?.toInt() ?: default
}

private inline fun SharedPreferences.getStringAsLong(key: String, default: Long): Long {
    return getString(key, default.toString())?.toLong() ?: default
}

private inline fun SharedPreferences.getStringAsFloat(key: String, default: Float): Float {
    return getString(key, default.toString())?.toFloat() ?: default
}

private inline fun SharedPreferences.getBooleanOrDefault(key: String, default: Boolean): Boolean {
    return getBoolean(key, default)
}

/**
 * Centralized manager for all app preferences.
 * Provides type-safe access to all settings with sensible defaults.
 */
class SharedPreferenceManager(private val context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val logger = Logger.getInstance(context)

    // ============================================
    // UI Preferences
    // ============================================

    /**
     * Gets background color preference.
     * Returns material theme color or parsed hex value.
     */
    fun getBgColor(): Int {
        val bgColor = preferences.getString("bgColor", "#00000000")
        if (bgColor == "material") {
            return getThemeColor(com.google.android.material.R.attr.colorOnPrimary)
        }
        return try {
            bgColor?.toColorInt() ?: 0x00000000.toInt()
        } catch (e: Exception) {
            logger.e("SharedPreferenceManager", "Error parsing bgColor: $bgColor", e)
            0x00000000.toInt()
        }
    }

    /**
     * Gets text color preference.
     * Returns material theme color or parsed hex value.
     */
    fun getTextColor(): Int {
        val textColor = getTextString()
        if (textColor == "material") {
            return getThemeColor(com.google.android.material.R.attr.colorPrimary)
        }
        return try {
            textColor?.toColorInt() ?: 0xFFF3F3F3.toInt()
        } catch (e: Exception) {
            logger.e("SharedPreferenceManager", "Error parsing textColor: $textColor", e)
            0xFFF3F3F3.toInt()
        }
    }

    /**
     * Gets raw text color string (for status bar logic).
     */
    fun getTextString(): String? {
        return preferences.getString("textColor", "#FFF3F3F3")
    }

    /**
     * Resolves theme color attribute.
     */
    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * Gets selected font family.
     */
    fun getTextFont(): String? {
        return preferences.getString("textFont", "system")
    }

    /**
     * Gets text style (normal, bold, italic, bold-italic).
     */
    fun getTextStyle(): String? {
        return preferences.getString("textStyle", "normal")
    }

    /**
     * Checks if text shadow is enabled.
     */
    fun isTextShadowEnabled(): Boolean = preferences.getBooleanOrDefault("textShadow", false)

    /**
     * Checks if status bar is visible.
     */
    fun isBarVisible(): Boolean = preferences.getBooleanOrDefault("barVisibility", false)

    /**
     * Checks if app drawer darkening is enabled.
     */
    fun isAppDrawerDarkeningEnabled(): Boolean = preferences.getBooleanOrDefault("appDrawerDarkening", true)

    /**
     * Checks if settings panel darkening is enabled.
     */
    fun isSettingsDarkeningEnabled(): Boolean = preferences.getBooleanOrDefault("settingsDarkening", true)

    /**
     * Checks if homescreen darkening is enabled.
     */
    fun isHomescreenDarkeningEnabled(): Boolean = preferences.getBooleanOrDefault("homescreenDarkening", false)

    /**
     * Gets animation speed in milliseconds.
     */
    fun getAnimationSpeed(): Long {
        return preferences.getStringAsLong("animationSpeed", 200)
    }

    /**
     * Gets swipe detection threshold in pixels.
     */
    fun getSwipeThreshold(): Int {
        return preferences.getStringAsInt("swipeThreshold", 100)
    }

    /**
     * Gets swipe velocity threshold.
     */
    fun getSwipeVelocity(): Int {
        return preferences.getStringAsInt("swipeVelocity", 100)
    }

    /**
     * Checks if launch confirmation dialog is enabled.
     */
    fun isConfirmationEnabled(): Boolean = preferences.getBooleanOrDefault("enableConfirmation", false)

    /**
     * Checks if auto rotation is blocked.
     */
    fun isAutoRotationBlocked(): Boolean = preferences.getBooleanOrDefault("blockAutoRotation", false)

    /**
     * Checks if settings require biometric authentication.
     */
    fun isSettingsLocked(): Boolean = preferences.getBooleanOrDefault("lockSettings", false)

    // ============================================
    // Clock/Date Preferences
    // ============================================

    /**
     * Checks if clock widget is enabled.
     */
    fun isClockEnabled(): Boolean = preferences.getBooleanOrDefault("clockEnabled", true)

    /**
     * Gets clock text alignment.
     */
    fun getClockAlignment(): String? {
        return preferences.getString("clockAlignment", "left")
    }

    /**
     * Gets clock text size preset.
     */
    fun getClockSize(): String? {
        return preferences.getString("clockSize", "medium")
    }

    /**
     * Checks if date display is enabled.
     */
    fun isDateEnabled(): Boolean = preferences.getBooleanOrDefault("dateEnabled", true)

    /**
     * Gets date text size preset.
     */
    fun getDateSize(): String? {
        return preferences.getString("dateSize", "medium")
    }

    // ============================================
    // Shortcut Preferences
    // ============================================

    /**
     * Saves shortcut configuration.
     * Format: componentName§splitter§profile§splitter§text§splitter§isContact
     */
    fun setShortcut(index: Int, text: CharSequence, componentName: String, profile: Int, isContact: Boolean = false) {
        preferences.edit {
            putString("shortcut${index}", "$componentName§splitter§$profile§splitter§${text}§splitter§${isContact}")
        }
    }

    /**
     * Gets shortcut configuration.
     * Returns null if not set (uses "e" as empty marker).
     */
    fun getShortcut(index: Int): List<String>? {
        val value = preferences.getString("shortcut${index}", "e§splitter§e§splitter§e§splitter§e")
        return value?.split("§splitter§")
    }

    /**
     * Gets number of enabled shortcuts.
     */
    fun getShortcutNumber(): Int {
        return preferences.getStringAsInt("shortcutNo", 4)
    }

    /**
     * Gets shortcut alignment.
     */
    fun getShortcutAlignment(): String? {
        return preferences.getString("shortcutAlignment", "left")
    }

    /**
     * Gets shortcut vertical alignment.
     */
    fun getShortcutVAlignment(): String? {
        return preferences.getString("shortcutVAlignment", "center")
    }

    /**
     * Gets shortcut size preset.
     */
    fun getShortcutSize(): String? {
        return preferences.getString("shortcutSize", "medium")
    }

    /**
     * Gets shortcut layout weight.
     */
    fun getShortcutWeight(): Float {
        return preferences.getStringAsFloat("shortcutWeight", 0.09f)
    }

    /**
     * Checks if shortcuts are locked (can't be changed).
     */
    fun areShortcutsLocked(): Boolean = preferences.getBooleanOrDefault("lockShortcuts", false)

    /**
     * Checks if notification dots are enabled.
     */
    fun isNotificationDotsEnabled(): Boolean = preferences.getBooleanOrDefault("notificationDots", false)

    /**
     * Checks if hidden apps should be shown in shortcut selection.
     */
    fun showHiddenShortcuts(): Boolean = preferences.getBooleanOrDefault("showHiddenShortcuts", true)

    // ============================================
    // Pinned Apps Preferences
    // ============================================

    /**
     * Toggles pin status for an app.
     * Uses string manipulation to add/remove from pinned list.
     */
    fun setPinnedApp(componentName: String, profile: Int) {
        preferences.edit {

            val pinnedAppString = when (isAppPinned(componentName, profile)) {
                true -> {
                    // Remove from list
                    getPinnedAppString()?.replace("§section§$componentName§splitter§$profile", "")
                }

                false -> {
                    // Add to list
                    "${getPinnedAppString()}§section§$componentName§splitter§$profile"
                }
            }

            putString(
                "pinnedApps",
                pinnedAppString
            )
        }
    }

    private fun getPinnedAppString(): String? {
        return preferences.getString("pinnedApps", "")
    }

    private fun getPinnedApps(): List<Pair<String, Int?>> {
        val pinnedApps = mutableListOf<Pair<String, Int?>>()
        val pinnedAppString = getPinnedAppString() ?: return pinnedApps
        val pinnedAppList = pinnedAppString.split("§section§")

        for (item in pinnedAppList) {
            if (item.isBlank()) continue
            val app = item.split("§splitter§")
            if (app.size > 1) {
                val profile = app.getOrNull(1)?.toIntOrNull()
                pinnedApps.add(Pair(app[0], profile))
            }
        }

        return pinnedApps
    }

    /**
     * Checks if an app is pinned.
     */
    fun isAppPinned(componentName: String, profile: Int): Boolean {
        return getPinnedApps().contains(Pair(componentName, profile))
    }

    // ============================================
    // Battery/Status
    // ============================================

    /**
     * Checks if battery display is enabled.
     */
    fun isBatteryEnabled(): Boolean = preferences.getBooleanOrDefault("batteryEnabled", false)

    // ============================================
    // Weather Preferences
    // ============================================

    /**
     * Checks if weather display is enabled.
     */
    fun isWeatherEnabled(): Boolean = preferences.getBooleanOrDefault("weatherEnabled", false)

    /**
     * Checks if GPS location is enabled for weather.
     */
    fun isWeatherGPS(): Boolean = preferences.getBooleanOrDefault("gpsLocation", false)

    /**
     * Sets GPS location preference.
     */
    fun setWeatherGPS(isEnabled: Boolean) {
        preferences.edit {
            putBoolean("gpsLocation", isEnabled)
        }
    }

    /**
     * Saves weather location (lat/lon format).
     */
    fun setWeatherLocation(location: String, region: String?) {
        preferences.edit {
            putString("location", location)
            putString("locationRegion", region)
        }
    }

    /**
     * Gets weather location string.
     */
    fun getWeatherLocation(): String? {
        return preferences.getString("location", "")
    }

    /**
     * Gets weather region/city name.
     */
    fun getWeatherRegion(): String? {
        return preferences.getString("locationRegion", "")
    }

    /**
     * Gets temperature unit preference.
     */
    fun getTempUnits(): String? {
        return preferences.getString("tempUnits", "celsius")
    }

    /**
     * Gets weather update interval in milliseconds.
     * Parses strings like "15m", "1h", "1d".
     */
    fun getWeatherUpdateIntervalMs(): Long {
        val defaultMs = 15 * 60_000L
        val raw = preferences.getString("weatherUpdateInterval", "15m")
        val ms = parseUpdateIntervalMs(raw, defaultMs)
        return ms.coerceAtLeast(60_000L)
    }

    /**
     * Parses update interval string to milliseconds.
     * Supports: "15" (minutes), "15m", "1h", "1d"
     */
    private fun parseUpdateIntervalMs(raw: String?, defaultMs: Long): Long {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return defaultMs

        // Bare numbers are treated as minutes
        if (s.all { it.isDigit() }) {
            val minutes = s.toLongOrNull() ?: return defaultMs
            if (minutes <= 0L) return defaultMs
            return minutes * 60_000L
        }

        // Parse with unit suffix
        val match = Regex("^(\\d+)\\s*([mhd])$").find(s) ?: return defaultMs
        val value = match.groupValues[1].toLongOrNull() ?: return defaultMs
        if (value <= 0L) return defaultMs

        val multiplier = when (match.groupValues[2]) {
            "m" -> 60_000L
            "h" -> 60 * 60_000L
            "d" -> 24 * 60 * 60_000L
            else -> return defaultMs
        }

        return try {
            Math.multiplyExact(value, multiplier)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }

    // ============================================
    // Gesture Preferences
    // ============================================

    /**
     * Checks if clock click gesture is enabled.
     */
    fun isClockGestureEnabled(): Boolean = preferences.getBooleanOrDefault("clockClick", true)

    /**
     * Checks if date click gesture is enabled.
     */
    fun isDateGestureEnabled(): Boolean = preferences.getBooleanOrDefault("dateClick", true)

    /**
     * Saves gesture app configuration.
     */
    fun setGestures(direction: String, appInfo: String?) {
        preferences.edit {
            putString("${direction}SwipeApp", appInfo)
        }
    }

    /**
     * Gets gesture app display name.
     */
    fun getGestureName(direction: String): String? {
        val name = preferences.getString("${direction}SwipeApp", "")?.split("§splitter§")
        return name?.get(0)
    }

    /**
     * Gets full gesture configuration.
     */
    fun getGestureInfo(direction: String): List<String>? {
        return preferences.getString("${direction}SwipeApp", "")?.split("§splitter§")
    }

    /**
     * Checks if a gesture direction is enabled.
     */
    fun isGestureEnabled(direction: String): Boolean = preferences.getBooleanOrDefault("${direction}Swipe", false)

    /**
     * Checks if double tap is enabled.
     */
    fun isDoubleTapEnabled(): Boolean = preferences.getBooleanOrDefault("doubleTap", false)

    /**
     * Gets double tap action (app or lock).
     * Handles migration from old preference format.
     */
    fun getDoubleTapAction(): String {
        val action = preferences.getString("doubleTapAction", null)
        if (action != null) {
            return action
        }

        // Migrate from old boolean preference
        val migratedAction = if (preferences.getBooleanOrDefault("doubleTapSwipe", false)) "app" else "lock"
        preferences.edit {
            putString("doubleTapAction", migratedAction)
            remove("doubleTapSwipe")
        }
        return migratedAction
    }

    // ============================================
    // App Menu Preferences
    // ============================================

    /**
     * Gets app menu text alignment.
     */
    fun getAppAlignment(): String? {
        return preferences.getString("appMenuAlignment", "left")
    }

    /**
     * Gets app menu text size preset.
     */
    fun getAppSize(): String? {
        return preferences.getString("appMenuSize", "medium")
    }

    /**
     * Checks if pin action is enabled.
     */
    fun isPinEnabled(): Boolean = preferences.getBooleanOrDefault("pinEnabled", true)

    /**
     * Checks if info action is enabled.
     */
    fun isInfoEnabled(): Boolean = preferences.getBooleanOrDefault("infoEnabled", true)

    /**
     * Checks if uninstall action is enabled.
     */
    fun isUninstallEnabled(): Boolean = preferences.getBooleanOrDefault("uninstallEnabled", true)

    /**
     * Checks if rename action is enabled.
     */
    fun isRenameEnabled(): Boolean = preferences.getBooleanOrDefault("renameEnabled", true)

    /**
     * Checks if hide action is enabled.
     */
    fun isHideEnabled(): Boolean = preferences.getBooleanOrDefault("hideEnabled", true)

    /**
     * Checks if close action is enabled.
     */
    fun isCloseEnabled(): Boolean = preferences.getBooleanOrDefault("closeEnabled", true)

    /**
     * Checks if search bar is enabled.
     */
    fun isSearchEnabled(): Boolean = preferences.getBooleanOrDefault("searchEnabled", true)

    /**
     * Gets search bar alignment.
     */
    fun getSearchAlignment(): String? {
        return preferences.getString("searchAlignment", "left")
    }

    /**
     * Gets search bar text size preset.
     */
    fun getSearchSize(): String? {
        return preferences.getString("searchSize", "medium")
    }

    /**
     * Checks if fuzzy search is enabled.
     */
    fun isFuzzySearchEnabled(): Boolean = preferences.getBooleanOrDefault("fuzzySearchEnabled", false)

    /**
     * Gets app item spacing in DP.
     */
    fun getAppSpacing(): Int {
        return preferences.getStringAsInt("appSpacing", 20)
    }

    /**
     * Checks if keyboard should auto-open on menu open.
     */
    fun isAutoKeyboardEnabled(): Boolean = preferences.getBooleanOrDefault("autoKeyboard", false)

    /**
     * Checks if single result should auto-launch.
     */
    fun isAutoLaunchEnabled(): Boolean = preferences.getBooleanOrDefault("autoLaunch", false)

    /**
     * Checks if contacts are enabled.
     */
    fun areContactsEnabled(): Boolean = preferences.getBooleanOrDefault("contactsEnabled", false)

    /**
     * Sets contacts enabled state.
     */
    fun setContactsEnabled(isEnabled: Boolean) {
        preferences.edit {
            putBoolean("contactsEnabled", isEnabled)
        }
    }

    /**
     * Checks if web search button is enabled.
     * Only available when search is on and auto-launch is off.
     */
    fun isWebSearchEnabled(): Boolean {
        return preferences.getBooleanOrDefault("webSearchEnabled", false) && isSearchEnabled() && !isAutoLaunchEnabled()
    }

    /**
     * Checks if alphabet index is enabled.
     */
    fun isAlphabetIndexEnabled(): Boolean = preferences.getBooleanOrDefault("alphabetIndexEnabled", false)

    /**
     * Gets alphabet index position (left/right).
     */
    fun getAlphabetIndexPosition(): String? {
        return preferences.getString("alphabetIndexPosition", "right")
    }

    // ============================================
    // Hidden Apps Preferences
    // ============================================

    /**
     * Sets app hidden state.
     */
    fun setAppHidden(componentName: String, profile: Int, hidden: Boolean) {
        preferences.edit {
            putBoolean("hidden$componentName-$profile", hidden)
        }
    }

    /**
     * Checks if app is hidden.
     */
    fun isAppHidden(componentName: String, profile: Int): Boolean {
        return preferences.getBoolean("hidden$componentName-$profile", false)
    }

    /**
     * Removes hidden flag (unhides app).
     */
    fun setAppVisible(componentName: String, profile: Int) {
        preferences.edit {
            remove("hidden$componentName-$profile")
        }
    }

    // ============================================
    // App Renaming Preferences
    // ============================================

    /**
     * Saves custom app name.
     */
    fun setAppName(componentName: String, profile: Int, newName: String) {
        preferences.edit {
            putString("name$componentName-$profile", newName)
        }
    }

    /**
     * Gets app name, using custom name if set.
     * Cleans up if name equals package name (app likely reset it).
     */
    fun getAppName(componentName: String, profile: Int, appName: CharSequence): CharSequence? {
        val key = "name$componentName-$profile"
        val savedName = preferences.getString(key, null)
        if (savedName.isNullOrBlank()) {
            // Clean up stale key if exists (async, won't block main thread)
            preferences.edit {
                remove(key)
            }
            return appName
        }

        // Clean up if saved name matches package name
        val packageName = try {
            componentName.substringBefore("/")
        } catch (e: Exception) {
            logger.w("SharedPreferenceManager", "Error parsing component name: $componentName")
            componentName
        }
        if (savedName == packageName || savedName == componentName) {
            // Clean up stale entry (async, won't block main thread)
            preferences.edit {
                remove(key)
            }
            return appName
        }

        return savedName
    }

    /**
     * Removes custom app name (resets to default).
     */
    fun resetAppName(componentName: String, profile: Int) {
        preferences.edit {
            remove("name$componentName-$profile")
        }
    }

    // ============================================
    // Reset Preferences
    // ============================================

    /**
     * Shows confirmation dialog for full reset.
     * Clears all preferences after confirmation.
     */
    fun resetAllPreferences() {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(context.getString(R.string.confirm_title))
            setMessage(context.getString(R.string.reset_confirm_text))
            setPositiveButton(context.getString(R.string.confirm_yes)) { _, _ ->
                performReset()
            }

            setNegativeButton(context.getString(R.string.confirm_no)) { _, _ ->
            }
        }.create().show()
    }

    /**
     * Performs the actual preference reset.
     * Sets isRestored flag to trigger UI refresh.
     */
    private fun performReset() {
        logger.i("SharedPreferenceManager", "Resetting all preferences")
        preferences.edit {
            clear()
            putBoolean("isRestored", true)
        }
    }
}
