package eu.ottop.yamlauncher.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Utility class for checking runtime permissions.
 * Provides a simple interface for verifying permission grants.
 */
class PermissionUtils {

    /**
     * Checks if a specific permission is granted.
     * Uses ContextCompat for consistent behavior across API levels.
     *
     * @param context Context for checking permissions
     * @param permission The permission to check (e.g., Manifest.permission.READ_CONTACTS)
     * @return true if permission is granted, false otherwise
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
