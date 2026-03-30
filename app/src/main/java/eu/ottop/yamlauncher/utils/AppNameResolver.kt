package eu.ottop.yamlauncher.utils

import android.content.Context
import android.content.pm.LauncherActivityInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves application labels from LauncherActivityInfo objects.
 * Handles caching to optimize repeated lookups.
 *
 * Provides intelligent fallback logic:
 * 1. First tries to use the activity-specific label
 * 2. Falls back to application label if activity label looks like a package name
 * 3. Falls back to package name as last resort
 */
object AppNameResolver {

    // Regex pattern to identify strings that look like package names
    // Matches: com.example.app, org.test.app, etc.
    private val packageNamePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.[A-Za-z][A-Za-z0-9_]*$")

    // Thread-safe cache for resolved labels
    // Uses component name as key to avoid re-resolving the same apps
    private val baseLabelCache = ConcurrentHashMap<String, String>()

    /**
     * Determines if a string likely represents a package name rather than a display label.
     * Used to detect when an app has set its label to the package name.
     *
     * @param value The string to check
     * @return true if string matches package name pattern
     */
    private fun isLikelyPackageName(value: String): Boolean {
        return packageNamePattern.matches(value)
    }

    /**
     * Resolves the best display label for an app.
     *
     * Resolution order:
     * 1. Activity label if it's user-friendly (not a package name)
     * 2. Application label if user-friendly
     * 3. Fallback to package name
     *
     * Results are cached for performance on repeated calls.
     * Safe to call from UI thread - uses in-memory cache and lazy PackageManager lookup.
     *
     * @param context Context for PackageManager access
     * @param appInfo The launcher activity info to resolve
     * @return The best available display label
     */
    fun resolveBaseLabel(context: Context, appInfo: LauncherActivityInfo): String {
        // Check cache first using component name as key
        val componentKey = appInfo.componentName.flattenToShortString()
        return baseLabelCache.getOrPut(componentKey) {
            val packageName = appInfo.applicationInfo.packageName
            val activityLabel = appInfo.label?.toString()?.trim().orEmpty()

            // Check if activity label looks like a package name (bad user label)
            val activityLooksLikePackageName = activityLabel == packageName || isLikelyPackageName(activityLabel)

            // Prefer activity label if it's user-friendly
            if (activityLabel.isNotEmpty() && !activityLooksLikePackageName) {
                return@getOrPut activityLabel
            }

            // Try application label as fallback
            val applicationLabel = appInfo.applicationInfo
                .loadLabel(context.packageManager)
                ?.toString()
                ?.trim()
                .orEmpty()
            val applicationLooksLikePackageName = applicationLabel == packageName || isLikelyPackageName(applicationLabel)

            // Resolution priority: friendly app label > friendly activity label > application label > package name
            when {
                applicationLabel.isNotEmpty() && !applicationLooksLikePackageName -> applicationLabel
                activityLabel.isNotEmpty() -> activityLabel
                applicationLabel.isNotEmpty() -> applicationLabel
                else -> packageName
            }
        }
    }
}
