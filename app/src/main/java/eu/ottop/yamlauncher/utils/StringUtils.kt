package eu.ottop.yamlauncher.utils

import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.regex.Pattern

/**
 * Utility class for string manipulation operations.
 * Provides methods for cleaning strings, fuzzy search patterns, and text formatting.
 */
class StringUtils {
    companion object {
        // Regex pattern to remove non-alphanumeric characters for search indexing
        // Keeps only letters (including unicode) and digits
        private val CLEAN_REGEX = Regex("[^\\p{L}0-9]")

        // Regex pattern to remove accent characters (diacritics)
        private val ACCENT_REGEX = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

        // LRU cache for fuzzy patterns (max 16 entries) - prevents regex recompilation
        // Thread-safe access via synchronized block in getFuzzyPattern
        private val fuzzyPatternCache = object : LinkedHashMap<String, Regex>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean {
                return size > 16
            }
        }
    }

    /**
     * Appends text to the end of a string if the value is not empty.
     * Useful for building formatted strings with separators.
     *
     * @param value The base string
     * @param addition The text to append
     * @return Combined string or original if value is empty
     */
    fun addEndTextIfNotEmpty(value: String, addition: String): String {
        return if (value.isNotEmpty()) "$value$addition" else value
    }

    /**
     * Prepends text to the beginning of a string if the value is not empty.
     * Useful for adding separators between non-empty values.
     *
     * @param value The base string
     * @param addition The text to prepend
     * @return Combined string or original if value is empty
     */
    fun addStartTextIfNotEmpty(value: String, addition: String): String {
        return if (value.isNotEmpty()) "$addition$value" else value
    }

    /**
     * Removes non-alphanumeric characters and accent characters (diacritics) from a string.
     * Used for search indexing and comparison.
     *
     * @param string The input string to clean
     * @return Cleaned string or null if input was null
     */
    fun cleanString(string: String?): String? {
        if (string == null) return null
        val cleaned = string.replace(CLEAN_REGEX, "")
        val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
        val withoutAccents = ACCENT_REGEX.matcher(normalized).replaceAll("")
        return withoutAccents.lowercase()
    }

    /**
     * Sets a clickable HTML link on a TextView.
     * Configures both the text content and link movement method.
     *
     * @param view The TextView to configure
     * @param link HTML-formatted link string
     */
    fun setLink(view: TextView, link: String) {
        view.text = Html.fromHtml(link, Html.FROM_HTML_MODE_LEGACY)
        view.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * Creates a Regex pattern for fuzzy matching in searches.
     * Converts query characters into a pattern that matches them in sequence.
     *
     * Uses LRU cache to avoid recompiling patterns for repeated queries.
     * Thread-safe via synchronized block.
     *
     * Examples:
     * - 'cl' -> 'c.*l' matches 'Clock', 'Calendar'
     * - 'msg' -> 'm.*s.*g' matches 'Messages'
     * - 'cmr' -> 'c.*m.*r' matches 'Camera'
     *
     * @param query The search query to convert
     * @return Compiled Regex pattern for fuzzy matching
     */
    fun getFuzzyPattern(query: String): Regex {
        synchronized(fuzzyPatternCache) {
            return fuzzyPatternCache.getOrPut(query) {
                // Escape special regex characters, then join with .* for fuzzy matching
                val regex = query.toCharArray().joinToString(".*") {
                    Regex.escape(it.toString())
                }
                Regex(regex, RegexOption.IGNORE_CASE)
            }
        }
    }
}
