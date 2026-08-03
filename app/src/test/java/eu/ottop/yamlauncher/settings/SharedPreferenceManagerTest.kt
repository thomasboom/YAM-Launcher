package eu.ottop.yamlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferenceManagerTest {
    @Test
    fun weatherIntervalParser_supportsMinutesHoursDaysAndBareMinutes() {
        assertEquals(15 * 60_000L, WeatherIntervalParser.parse("15m"))
        assertEquals(2 * 60 * 60_000L, WeatherIntervalParser.parse("2h"))
        assertEquals(7 * 24 * 60 * 60_000L, WeatherIntervalParser.parse("7d"))
        assertEquals(30 * 60_000L, WeatherIntervalParser.parse("30"))
    }

    @Test
    fun weatherIntervalParser_fallsBackForInvalidAndNonPositiveValues() {
        val default = 15 * 60_000L
        assertEquals(default, WeatherIntervalParser.parse(null))
        assertEquals(default, WeatherIntervalParser.parse("invalid"))
        assertEquals(default, WeatherIntervalParser.parse("0m"))
        assertEquals(default, WeatherIntervalParser.parse("-1m"))
    }

    @Test
    fun weatherIntervalParser_doesNotOverflow() {
        assertEquals(Long.MAX_VALUE, WeatherIntervalParser.parse("999999999999999999d"))
    }

    @Test
    fun preferenceValues_recoverLegacyPrimitiveTypes() {
        assertEquals("42", PreferenceValues.asString(42, "default"))
        assertEquals(42, PreferenceValues.asInt("42", 0))
        assertTrue(PreferenceValues.asBoolean("true", false))
        assertFalse(PreferenceValues.asBoolean(0, true))
        assertEquals(3.5f, PreferenceValues.asFloat("3.5", 0f))
    }

    @Test
    fun preferenceValues_useDefaultsForCorruptValues() {
        assertEquals(7, PreferenceValues.asInt("oops", 7))
        assertEquals(2f, PreferenceValues.asFloat("NaN", 2f))
        assertTrue(PreferenceValues.asBoolean("yes", true))
    }

    @Test
    fun shortcutSetting_roundTripsValidData() {
        val shortcut = ShortcutSetting("example/app.Main", 1, "Example §splitter§ Work", false)
        assertEquals(shortcut, ShortcutSetting.decode(shortcut.encode()))
    }

    @Test
    fun shortcutSetting_rejectsMalformedData() {
        assertNull(ShortcutSetting.decode(null))
        assertNull(ShortcutSetting.decode("e§splitter§e§splitter§e§splitter§e"))
        assertNull(ShortcutSetting.decode("component§splitter§bad§splitter§Name§splitter§false"))
        assertNull(ShortcutSetting.decode("component§splitter§-1§splitter§Name§splitter§false"))
    }

    @Test
    fun gestureSetting_rejectsMissingOrInvalidFields() {
        assertEquals(
            GestureSetting("Example §splitter§ Work", "example/app.Main", 0),
            GestureSetting.decode("Example §splitter§ Work§splitter§example/app.Main§splitter§0"),
        )
        assertNull(GestureSetting.decode("Example§splitter§component"))
        assertNull(GestureSetting.decode("Example§splitter§component§splitter§bad"))
    }
}
