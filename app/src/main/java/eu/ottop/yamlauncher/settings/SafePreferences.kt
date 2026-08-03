package eu.ottop.yamlauncher.settings

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Small defensive boundary around SharedPreferences.
 *
 * Android's typed getters throw ClassCastException when a backup or an older
 * version stored a key with another type. Reading from [SharedPreferences.all]
 * lets us recover such values and rewrite them in the format used by the
 * current preference XML.
 */
internal class SafePreferences(private val preferences: SharedPreferences) {
    fun string(key: String, default: String): String {
        return PreferenceValues.asString(preferences.all[key], default)
    }

    fun boolean(key: String, default: Boolean): Boolean {
        return PreferenceValues.asBoolean(preferences.all[key], default)
    }

    fun intFromString(key: String, default: Int, range: IntRange? = null): Int {
        val value = PreferenceValues.asInt(preferences.all[key], default)
        return if (range == null) value else value.coerceIn(range)
    }

    fun longFromString(key: String, default: Long, range: LongRange? = null): Long {
        val value = PreferenceValues.asLong(preferences.all[key], default)
        return if (range == null) value else value.coerceIn(range)
    }

    fun floatFromString(key: String, default: Float, range: ClosedFloatingPointRange<Float>? = null): Float {
        val value = PreferenceValues.asFloat(preferences.all[key], default)
        return if (range == null) value else value.coerceIn(range)
    }

    fun putString(key: String, value: String?) = preferences.edit { putString(key, value) }

    fun putBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

    fun remove(key: String) = preferences.edit { remove(key) }

    fun clear(restoredKey: String) = preferences.edit {
        clear()
        putBoolean(restoredKey, true)
    }

    /** Repairs values before PreferenceFragmentCompat invokes strict typed getters. */
    fun repairKnownTypes() {
        val values = preferences.all
        preferences.edit {
            PreferenceSchema.stringDefaults.forEach { (key, default) ->
                val value = values[key] ?: return@forEach
                if (value !is String) putString(key, PreferenceValues.asString(value, default))
            }
            PreferenceSchema.booleanDefaults.forEach { (key, default) ->
                val value = values[key] ?: return@forEach
                if (value !is Boolean) putBoolean(key, PreferenceValues.asBoolean(value, default))
            }
        }
    }
}

internal object PreferenceValues {
    fun asString(value: Any?, default: String): String = when (value) {
        null -> default
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> default
    }

    fun asBoolean(value: Any?, default: Boolean): Boolean = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull() ?: default
        is Number -> when (value.toInt()) {
            0 -> false
            1 -> true
            else -> default
        }
        else -> default
    }

    fun asInt(value: Any?, default: Int): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    } ?: default

    fun asLong(value: Any?, default: Long): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    } ?: default

    fun asFloat(value: Any?, default: Float): Float = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }?.takeIf { it.isFinite() } ?: default
}

/** Keys declared in preference XML, grouped by their persisted type. */
internal object PreferenceSchema {
    val stringDefaults = mapOf(
        "bgColor" to "#00000000",
        "textColor" to "#FFF3F3F3",
        "textFont" to "system",
        "textStyle" to "normal",
        "animationSpeed" to "200",
        "swipeThreshold" to "100",
        "swipeVelocity" to "100",
        "clockAlignment" to "left",
        "clockSize" to "medium",
        "dateSize" to "medium",
        "tempUnits" to "celsius",
        "weatherUpdateInterval" to "15m",
        "shortcutNo" to "4",
        "shortcutAlignment" to "left",
        "shortcutVAlignment" to "center",
        "shortcutSize" to "medium",
        "shortcutWeight" to "0.11",
        "doubleTapAction" to "lock",
        "appMenuAlignment" to "left",
        "appMenuSize" to "medium",
        "searchAlignment" to "left",
        "searchSize" to "medium",
        "appSpacing" to "20",
        "alphabetIndexPosition" to "right",
    )

    val booleanDefaults = mapOf(
        "textShadow" to false, "barVisibility" to false,
        "appDrawerDarkening" to true,
        "settingsDarkening" to true,
        "homescreenDarkening" to false, "enableConfirmation" to false,
        "blockAutoRotation" to false, "lockSettings" to false,
        "clockEnabled" to true, "dateEnabled" to true,
        "lockShortcuts" to false, "notificationDots" to false,
        "showHiddenShortcuts" to true, "batteryEnabled" to false,
        "weatherEnabled" to false, "gpsLocation" to false,
        "clockClick" to true, "dateClick" to true,
        "clockSwipe" to false, "dateSwipe" to false,
        "leftSwipe" to false, "rightSwipe" to false,
        "doubleTap" to false, "pinEnabled" to true, "infoEnabled" to true,
        "uninstallEnabled" to true, "renameEnabled" to true,
        "hideEnabled" to true, "closeEnabled" to true,
        "searchEnabled" to true, "fuzzySearchEnabled" to false,
        "appStrictClick" to false, "autoKeyboard" to false,
        "autoLaunch" to false, "contactsEnabled" to false,
        "webSearchEnabled" to false, "alphabetIndexEnabled" to false,
    )
}

internal data class ShortcutSetting(
    val componentName: String,
    val profile: Int,
    val label: String,
    val isContact: Boolean,
) {
    fun encode(): String = listOf(componentName, profile, label, isContact).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "§splitter§"

        fun decode(raw: String?): ShortcutSetting? {
            val parts = raw?.split(SEPARATOR) ?: return null
            if (parts.size < 4 || parts[0].isBlank() || parts[0] == "e") return null
            val profile = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val isContact = parts.last().toBooleanStrictOrNull() ?: false
            val label = parts.subList(2, parts.lastIndex).joinToString(SEPARATOR)
            return ShortcutSetting(parts[0], profile, label, isContact)
        }
    }
}

internal data class GestureSetting(val name: String, val componentName: String, val profile: Int) {
    companion object {
        private const val SEPARATOR = "§splitter§"

        fun decode(raw: String?): GestureSetting? {
            val parts = raw?.split(SEPARATOR) ?: return null
            if (parts.size < 3 || parts[0].isBlank() || parts[1].isBlank()) return null
            val profile = parts.last().toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val componentName = parts[parts.lastIndex - 1].takeIf { it.isNotBlank() } ?: return null
            val name = parts.dropLast(2).joinToString(SEPARATOR).takeIf { it.isNotBlank() } ?: return null
            return GestureSetting(name, componentName, profile)
        }
    }
}

internal object WeatherIntervalParser {
    private const val DEFAULT_INTERVAL_MS = 15 * 60_000L
    private const val MIN_INTERVAL_MS = 60_000L

    fun parse(raw: String?): Long {
        val input = raw?.trim()?.lowercase().orEmpty()
        val match = Regex("^(\\d+)\\s*([mhd]?)$").matchEntire(input) ?: return DEFAULT_INTERVAL_MS
        val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return DEFAULT_INTERVAL_MS
        val multiplier = when (match.groupValues[2]) {
            "", "m" -> 60_000L
            "h" -> 60 * 60_000L
            "d" -> 24 * 60 * 60_000L
            else -> return DEFAULT_INTERVAL_MS
        }
        return runCatching { Math.multiplyExact(amount, multiplier) }
            .getOrDefault(Long.MAX_VALUE)
            .coerceAtLeast(MIN_INTERVAL_MS)
    }
}
