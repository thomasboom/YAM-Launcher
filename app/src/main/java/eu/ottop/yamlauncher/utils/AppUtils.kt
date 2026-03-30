package eu.ottop.yamlauncher.utils

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat.getString
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for app management operations.
 * Handles launching apps, getting installed apps, and confirmation dialogs.
 */
class AppUtils(private val context: Context, private val launcherApps: LauncherApps) {

    private val sharedPreferenceManager = SharedPreferenceManager(context)
    private val logger = Logger.getInstance(context)

    /**
     * Gets list of installed launchable apps.
     * Includes apps from all user profiles (personal and work).
     * Filters out hidden apps unless showApps is true.
     *
     * @param showApps If true, includes hidden apps (used for shortcut selection)
     * @return List of (LauncherActivityInfo, UserHandle, profileIndex) triples
     * @suspend Must be called from coroutine context
     */
    suspend fun getInstalledApps(showApps: Boolean = false): List<Triple<LauncherActivityInfo, UserHandle, Int>> {
        val allApps = mutableListOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
        var sortedApps = listOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
        withContext(Dispatchers.Default) {
            // Iterate through all user profiles (normal and work)
            for (i in launcherApps.profiles.indices) {
                launcherApps.getActivityList(null, launcherApps.profiles[i]).forEach { app ->
                    // Include app if not hidden OR if showing hidden apps for shortcut selection
                    // Also exclude the launcher itself from the list
                    if ((!sharedPreferenceManager.isAppHidden(
                            app.componentName.flattenToString(),
                            i
                        ) or showApps)&& app.applicationInfo.packageName != context.applicationInfo.packageName
                    ) {
                        // Store app info with profile index (i) to identify personal vs work profile
                        allApps.add(Triple(app, launcherApps.profiles[i], i))
                    }
                }
            }

            // Sort apps: pinned apps first, then alphabetically by name
            sortedApps = allApps.sortedWith(
                compareBy<Triple<LauncherActivityInfo, UserHandle, Int>> {
                    // Invert pinned status so pinned apps come first
                    !sharedPreferenceManager.isAppPinned(it.first.componentName.flattenToString(), it.third)
                }.thenBy {
                // Then sort alphabetically (case-insensitive)
                sharedPreferenceManager.getAppName(
                    it.first.componentName.flattenToString(),
                    it.third,
                    AppNameResolver.resolveBaseLabel(context, it.first)
                ).toString().lowercase()
                }
            )
        }
        return sortedApps

    }

    /**
     * Gets list of hidden apps for the hidden apps settings screen.
     * Used to allow users to unhide apps.
     *
     * @return List of hidden apps as (LauncherActivityInfo, UserHandle, profileIndex) triples
     * @suspend Must be called from coroutine context
     */
    suspend fun getHiddenApps(): List<Triple<LauncherActivityInfo, UserHandle, Int>> {
        val allApps = mutableListOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
        var sortedApps = listOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
        withContext(Dispatchers.Default) {
        for (i in launcherApps.profiles.indices) {
            launcherApps.getActivityList(null, launcherApps.profiles[i]).forEach { app ->
                // Only include apps that are marked as hidden
                if (sharedPreferenceManager.isAppHidden(app.componentName.flattenToString(), i)) {
                    allApps.add(Triple(app, launcherApps.profiles[i], i))
                }
            }
        }

        // Sort hidden apps alphabetically
        sortedApps = allApps.sortedBy {
        sharedPreferenceManager.getAppName(
            it.first.componentName.flattenToString(),
            it.third,
            AppNameResolver.resolveBaseLabel(context, it.first)
        ).toString().lowercase()
        }
        }
        return sortedApps
    }

    /**
     * Gets ApplicationInfo for a specific package and profile.
     * Used to check if an app is still installed.
     *
     * @param packageName Package name to look up
     * @param profile Profile index (0 for personal, 1+ for work profiles)
     * @return ApplicationInfo or null if not found
     */
    fun getAppInfo(
        packageName: String,
        profile: Int
    ): ApplicationInfo? {
        return try {
            if (profile !in launcherApps.profiles.indices) {
                return null
            }
            launcherApps.getApplicationInfo(packageName, 0, launcherApps.profiles[profile])
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Internal method to start an app.
     * Handles the actual launch and error reporting.
     *
     * @param componentName App component to launch
     * @param userHandle User profile to launch in
     * @return true if launch succeeded, false otherwise
     */
    private fun startApp(componentName: ComponentName, userHandle: UserHandle): Boolean {
        return try {
            launcherApps.startMainActivity(componentName, userHandle, null, null)
            logger.i("AppUtils", "Launched app: ${componentName.packageName}")
            true
        } catch (e: Exception) {
            logger.e("AppUtils", "Failed to launch app: ${componentName.packageName}", e)
            showLaunchError()
            false
        }
    }

    /**
     * Launches an app with optional confirmation dialog.
     * Shows confirmation if user has enabled that preference.
     *
     * @param componentName App component to launch
     * @param userHandle User profile to launch in
     */
    fun launchApp(componentName: ComponentName, userHandle: UserHandle) {
        if (sharedPreferenceManager.isConfirmationEnabled()) {
            showConfirmationDialog(componentName, userHandle)
        } else {
            startApp(componentName, userHandle)
        }
    }

    /**
     * Shows confirmation dialog before launching an app.
     * Used when "confirm before launch" preference is enabled.
     */
    private fun showConfirmationDialog(componentName: ComponentName, userHandle: UserHandle) {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(getString(context, R.string.confirm_title))
            setMessage(getString(context, R.string.launch_confirmation_text))

            setPositiveButton(getString(context, R.string.confirm_yes)) { _, _ ->
                startApp(componentName, userHandle)
            }

            setNegativeButton(getString(context, R.string.confirm_no)) { _, _ ->
                // User cancelled, do nothing
            }

        }.create().show()
    }

    /**
     * Shows toast message for launch errors.
     * Posted to main thread to ensure proper display.
     */
    private fun showLaunchError() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, getString(context, R.string.launch_error), Toast.LENGTH_SHORT).show()
        }
    }
}
