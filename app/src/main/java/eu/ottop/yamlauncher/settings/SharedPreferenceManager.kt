package eu.ottop.yamlauncher.settings

import android.content.Context
import android.util.TypedValue
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import eu.ottop.yamlauncher.utils.Logger

/**
 * Centralized manager for all app preferences.
 * Provides type-safe access to all settings with sensible defaults.
 */
class SharedPreferenceManager(private val context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val store = SafePreferences(preferences)
    private val logger = Logger.getInstance(context)

    init {
        store.repairKnownTypes()
    }

    /** Revalidates values after an external restore operation. */
    fun repairPreferences() {
        store.repairKnownTypes()
    }

    // ============================================
    // UI Preferences
    // ============================================

    /**
     * Gets background color preference.
     * Returns material theme color or parsed hex value.
     */
    fun getBgColor(): Int {
        val bgColor = store.string("bgColor", "#00000000")
        if (bgColor == "material") {
            return getThemeColor(com.google.android.material.R.attr.colorOnPrimary)
        }
        return try {
            bgColor.toColorInt()
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
            return getThemeColor(androidx.appcompat.R.attr.colorPrimary)
        }
        return try {
            textColor.toColorInt()
        } catch (e: Exception) {
            logger.e("SharedPreferenceManager", "Error parsing textColor: $textColor", e)
            0xFFF3F3F3.toInt()
        }
    }

    /**
     * Gets raw text color string (for status bar logic).
     */
    fun getTextString(): String {
        return store.string("textColor", "#FFF3F3F3")
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
    fun getTextFont(): String {
        return store.string("textFont", "system")
    }

    /**
     * Gets text style (normal, bold, italic, bold-italic).
     */
    fun getTextStyle(): String {
        return store.string("textStyle", "normal")
    }

    /**
     * Checks if text shadow is enabled.
     */
    fun isTextShadowEnabled(): Boolean = store.boolean("textShadow", false)

    /**
     * Checks if status bar is visible.
     */
    fun isBarVisible(): Boolean = store.boolean("barVisibility", false)

    /**
     * Checks if app drawer darkening is enabled.
     */
    fun isAppDrawerDarkeningEnabled(): Boolean = store.boolean("appDrawerDarkening", true)

    /**
     * Checks if homescreen darkening is enabled.
     */
    fun isHomescreenDarkeningEnabled(): Boolean = store.boolean("homescreenDarkening", false)

    /**
     * Gets animation speed in milliseconds.
     */
    fun getAnimationSpeed(): Long {
        return store.longFromString("animationSpeed", 200, 0L..10_000L)
    }

    /**
     * Gets swipe detection threshold in pixels.
     */
    fun getSwipeThreshold(): Int {
        return store.intFromString("swipeThreshold", 100, 1..10_000)
    }

    /**
     * Gets swipe velocity threshold.
     */
    fun getSwipeVelocity(): Int {
        return store.intFromString("swipeVelocity", 100, 1..100_000)
    }

    /**
     * Checks if launch confirmation dialog is enabled.
     */
    fun isConfirmationEnabled(): Boolean = store.boolean("enableConfirmation", false)

    /**
     * Checks if auto rotation is blocked.
     */
    fun isAutoRotationBlocked(): Boolean = store.boolean("blockAutoRotation", false)

    /**
     * Checks if settings require biometric authentication.
     */
    fun isSettingsLocked(): Boolean = store.boolean("lockSettings", false)

    // ============================================
    // Clock/Date Preferences
    // ============================================

    /**
     * Checks if clock widget is enabled.
     */
    fun isClockEnabled(): Boolean = store.boolean("clockEnabled", true)

    /**
     * Gets clock text alignment.
     */
    fun getClockAlignment(): String {
        return store.string("clockAlignment", "left")
    }

    /**
     * Gets clock text size preset.
     */
    fun getClockSize(): String {
        return store.string("clockSize", "medium")
    }

    /**
     * Checks if date display is enabled.
     */
    fun isDateEnabled(): Boolean = store.boolean("dateEnabled", true)

    /**
     * Gets date text size preset.
     */
    fun getDateSize(): String {
        return store.string("dateSize", "medium")
    }

    // ============================================
    // Shortcut Preferences
    // ============================================

    /**
     * Saves shortcut configuration.
     * Format: componentName§splitter§profile§splitter§text§splitter§isContact
     */
    fun setShortcut(index: Int, text: CharSequence, componentName: String, profile: Int, isContact: Boolean = false) {
        if (index < 0 || profile < 0 || componentName.isBlank()) return
        val setting = ShortcutSetting(componentName, profile, text.toString(), isContact)
        store.putString("shortcut$index", setting.encode())
    }

    /**
     * Gets shortcut configuration.
     * Returns null if not set (uses "e" as empty marker).
     */
    fun getShortcut(index: Int): List<String>? {
        val setting = getShortcutSetting(index) ?: return null
        return listOf(setting.componentName, setting.profile.toString(), setting.label, setting.isContact.toString())
    }

    internal fun getShortcutSetting(index: Int): ShortcutSetting? {
        if (index < 0) return null
        return ShortcutSetting.decode(store.string("shortcut$index", ""))
    }

    /**
     * Gets number of enabled shortcuts.
     */
    fun getShortcutNumber(): Int {
        return store.intFromString("shortcutNo", 4, 0..8)
    }

    /**
     * Gets shortcut alignment.
     */
    fun getShortcutAlignment(): String {
        return store.string("shortcutAlignment", "left")
    }

    /**
     * Gets shortcut vertical alignment.
     */
    fun getShortcutVAlignment(): String {
        return store.string("shortcutVAlignment", "center")
    }

    /**
     * Gets shortcut size preset.
     */
    fun getShortcutSize(): String {
        return store.string("shortcutSize", "medium")
    }

    /**
     * Gets shortcut layout weight.
     */
    fun getShortcutWeight(): Float {
        return store.floatFromString("shortcutWeight", 0.11f, 0.01f..1f)
    }

    /**
     * Checks if shortcuts are locked (can't be changed).
     */
    fun areShortcutsLocked(): Boolean = store.boolean("lockShortcuts", false)

    /**
     * Checks if notification dots are enabled.
     */
    fun isNotificationDotsEnabled(): Boolean = store.boolean("notificationDots", false)

    /**
     * Checks if hidden apps should be shown in shortcut selection.
     */
    fun showHiddenShortcuts(): Boolean = store.boolean("showHiddenShortcuts", true)

    // ============================================
    // Pinned Apps Preferences
    // ============================================

    /**
     * Toggles pin status for an app.
     * Uses string manipulation to add/remove from pinned list.
     */
    fun setPinnedApp(componentName: String, profile: Int) {
        if (componentName.isBlank() || profile < 0) return
        val app = componentName to profile
        val pinnedApps = getPinnedApps().toMutableSet()
        if (!pinnedApps.add(app)) pinnedApps.remove(app)
        val encoded = pinnedApps.joinToString("§section§") { (component, profileIndex) ->
            "$component§splitter§$profileIndex"
        }
        store.putString("pinnedApps", encoded)
    }

    private fun getPinnedApps(): Set<Pair<String, Int>> {
        return store.string("pinnedApps", "")
            .split("§section§")
            .mapNotNull { item ->
                val parts = item.split("§splitter§", limit = 2)
                val profile = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
                parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { component ->
                    profile?.let { component to it }
                }
            }
            .toSet()
    }

    /**
     * Checks if an app is pinned.
     */
    fun isAppPinned(componentName: String, profile: Int): Boolean {
        return componentName to profile in getPinnedApps()
    }

    // ============================================
    // Battery/Status
    // ============================================

    /**
     * Checks if battery display is enabled.
     */
    fun isBatteryEnabled(): Boolean = store.boolean("batteryEnabled", false)

    // ============================================
    // Weather Preferences
    // ============================================

    /**
     * Checks if weather display is enabled.
     */
    fun isWeatherEnabled(): Boolean = store.boolean("weatherEnabled", false)

    /** Checks if Curbox screen time is displayed below the clock. */
    fun isScreenTimeEnabled(): Boolean = store.boolean("screenTimeEnabled", false)

    /**
     * Checks if GPS location is enabled for weather.
     */
    fun isWeatherGPS(): Boolean = store.boolean("gpsLocation", false)

    /**
     * Sets GPS location preference.
     */
    fun setWeatherGPS(isEnabled: Boolean) {
        store.putBoolean("gpsLocation", isEnabled)
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
    fun getWeatherLocation(): String {
        return store.string("location", "")
    }

    /**
     * Gets weather region/city name.
     */
    fun getWeatherRegion(): String {
        return store.string("locationRegion", "")
    }

    /**
     * Gets temperature unit preference.
     */
    fun getTempUnits(): String {
        return store.string("tempUnits", "celsius")
    }

    /**
     * Gets weather update interval in milliseconds.
     * Parses strings like "15m", "1h", "1d".
     */
    fun getWeatherUpdateIntervalMs(): Long {
        return WeatherIntervalParser.parse(store.string("weatherUpdateInterval", "15m"))
    }

    // ============================================
    // Gesture Preferences
    // ============================================

    /**
     * Checks if clock click gesture is enabled.
     */
    fun isClockGestureEnabled(): Boolean = store.boolean("clockClick", true)

    /**
     * Checks if date click gesture is enabled.
     */
    fun isDateGestureEnabled(): Boolean = store.boolean("dateClick", true)

    /**
     * Saves gesture app configuration.
     */
    fun setGestures(direction: String, appInfo: String?) {
        val key = gestureAppKey(direction) ?: return
        if (appInfo == null) {
            store.remove(key)
        } else if (GestureSetting.decode(appInfo) != null) {
            store.putString(key, appInfo)
        }
    }

    /**
     * Gets gesture app display name.
     */
    fun getGestureName(direction: String): String? {
        return getGestureSetting(direction)?.name
    }

    /**
     * Gets full gesture configuration.
     */
    fun getGestureInfo(direction: String): List<String>? {
        val setting = getGestureSetting(direction) ?: return null
        return listOf(setting.name, setting.componentName, setting.profile.toString())
    }

    internal fun getGestureSetting(direction: String): GestureSetting? {
        val key = gestureAppKey(direction) ?: return null
        return GestureSetting.decode(store.string(key, ""))
    }

    /**
     * Checks if a gesture direction is enabled.
     */
    fun isGestureEnabled(direction: String): Boolean {
        if (direction !in GESTURE_DIRECTIONS) return false
        return store.boolean("${direction}Swipe", false)
    }

    private fun gestureAppKey(direction: String): String? {
        return direction.takeIf { it in GESTURE_DIRECTIONS }?.let { "${it}SwipeApp" }
    }

    /**
     * Checks if double tap is enabled.
     */
    fun isDoubleTapEnabled(): Boolean = store.boolean("doubleTap", false)

    /**
     * Gets double tap action (app or lock).
     * Handles migration from old preference format.
     */
    fun getDoubleTapAction(): String {
        val action = store.string("doubleTapAction", "")
        if (action == "app" || action == "lock") {
            return action
        }

        // Migrate from old boolean preference
        val migratedAction = if (store.boolean("doubleTapSwipe", false)) "app" else "lock"
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
    fun getAppAlignment(): String {
        return store.string("appMenuAlignment", "left")
    }

    /**
     * Gets app menu text size preset.
     */
    fun getAppSize(): String {
        return store.string("appMenuSize", "medium")
    }

    /**
     * Checks if pin action is enabled.
     */
    fun isPinEnabled(): Boolean = store.boolean("pinEnabled", true)

    /**
     * Checks if info action is enabled.
     */
    fun isInfoEnabled(): Boolean = store.boolean("infoEnabled", true)

    /**
     * Checks if uninstall action is enabled.
     */
    fun isUninstallEnabled(): Boolean = store.boolean("uninstallEnabled", true)

    /**
     * Checks if rename action is enabled.
     */
    fun isRenameEnabled(): Boolean = store.boolean("renameEnabled", true)

    /**
     * Checks if hide action is enabled.
     */
    fun isHideEnabled(): Boolean = store.boolean("hideEnabled", true)

    /**
     * Checks if close action is enabled.
     */
    fun isCloseEnabled(): Boolean = store.boolean("closeEnabled", true)

    /**
     * Checks if search bar is enabled.
     */
    fun isSearchEnabled(): Boolean = store.boolean("searchEnabled", true)

    /**
     * Gets search bar alignment.
     */
    fun getSearchAlignment(): String {
        return store.string("searchAlignment", "left")
    }

    /**
     * Gets search bar text size preset.
     */
    fun getSearchSize(): String {
        return store.string("searchSize", "medium")
    }

    /**
     * Checks if fuzzy search is enabled.
     */
    fun isFuzzySearchEnabled(): Boolean = store.boolean("fuzzySearchEnabled", false)

    /**
     * Gets app item spacing in DP.
     */
    fun getAppSpacing(): Int {
        return store.intFromString("appSpacing", 20, 0..200)
    }

    /**
     * Checks if strict text clicking is enabled.
     * When enabled, only clicking directly on the text triggers the app launch.
     */
    fun isAppStrictClickEnabled(): Boolean = store.boolean("appStrictClick", false)

    /**
     * Checks if keyboard should auto-open on menu open.
     */
    fun isAutoKeyboardEnabled(): Boolean = store.boolean("autoKeyboard", false)

    /**
     * Checks if single result should auto-launch.
     */
    fun isAutoLaunchEnabled(): Boolean = store.boolean("autoLaunch", false)

    /**
     * Checks if contacts are enabled.
     */
    fun areContactsEnabled(): Boolean = store.boolean("contactsEnabled", false)

    /**
     * Sets contacts enabled state.
     */
    fun setContactsEnabled(isEnabled: Boolean) {
        store.putBoolean("contactsEnabled", isEnabled)
    }

    /**
     * Checks if web search button is enabled.
     * Only available when search is on and auto-launch is off.
     */
    fun isWebSearchEnabled(): Boolean {
        return store.boolean("webSearchEnabled", false) && isSearchEnabled() && !isAutoLaunchEnabled()
    }

    /**
     * Checks if alphabet index is enabled.
     */
    fun isAlphabetIndexEnabled(): Boolean = store.boolean("alphabetIndexEnabled", false)

    /**
     * Gets alphabet index position (left/right).
     */
    fun getAlphabetIndexPosition(): String {
        return store.string("alphabetIndexPosition", "right")
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
        return store.boolean("hidden$componentName-$profile", false)
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
        val savedName = store.string(key, "")
        if (savedName.isNullOrBlank()) return appName

        // Clean up if saved name matches package name or the component itself
        val packageName = componentName.substringBefore("/")
        return if (savedName == packageName || savedName == componentName) appName else savedName
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

    /** Clears settings after the UI has obtained user confirmation. */
    fun clearAllPreferences() {
        logger.i("SharedPreferenceManager", "Resetting all preferences")
        store.clear("isRestored")
    }

    private companion object {
        val GESTURE_DIRECTIONS = setOf("clock", "date", "left", "right", "doubleTap", "weather")
    }
}
