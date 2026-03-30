package eu.ottop.yamlauncher.tasks

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.ottop.yamlauncher.utils.Logger

/**
 * NotificationListenerService for monitoring notifications.
 * Tracks which apps have notifications for badge display.
 *
 * Features:
 * - Singleton pattern for easy access
 * - Broadcast updates to MainActivity
 * - Permission checking
 */
class NotificationListener : NotificationListenerService() {

    private val logger = Logger.getInstance(this)

    companion object {
        // Action broadcast when notification state changes
        const val ACTION_NOTIFICATIONS_CHANGED = "eu.ottop.yamlauncher.NOTIFICATIONS_CHANGED"

        // Singleton instance for external access
        private var instance: NotificationListener? = null

        /**
         * Gets the current NotificationListener instance.
         * Use to check if service is connected and access notification data.
         */
        fun getInstance(): NotificationListener? = instance

        /**
         * Checks if the notification listener is enabled.
         * Verifies user has granted notification access permission.
         *
         * @param context Context for Settings.Secure access
         * @return true if notification listener is enabled
         */
        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, NotificationListener::class.java)
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return enabledListeners?.contains(componentName.flattenToString()) == true
        }

        /**
         * Opens system settings for user to enable notification access.
         * Should be called when user tries to enable notification dots.
         */
        fun requestPermission(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        logger.i("NotificationListener", "NotificationListener service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        logger.i("NotificationListener", "NotificationListener service destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        logger.i("NotificationListener", "NotificationListener connected")
        // Notify listeners that we're connected
        broadcastUpdate()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        logger.i("NotificationListener", "NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            sbn?.let {
                logger.d("NotificationListener", "Notification posted from: ${it.packageName}")
            }
            broadcastUpdate()
        } catch (e: Exception) {
            logger.e("NotificationListener", "Error in onNotificationPosted", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            sbn?.let {
                logger.d("NotificationListener", "Notification removed from: ${it.packageName}")
            }
            broadcastUpdate()
        } catch (e: Exception) {
            logger.e("NotificationListener", "Error in onNotificationRemoved", e)
        }
    }

    /**
     * Broadcasts notification state change to MainActivity.
     * Triggers UI update for notification badges.
     */
    private fun broadcastUpdate() {
        val intent = Intent(ACTION_NOTIFICATIONS_CHANGED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Checks if a specific package has any active notifications.
     *
     * @param packageName Package to check
     * @return true if package has notifications
     */
    fun hasNotification(packageName: String): Boolean {
        val notifications = activeNotifications
        return notifications.any { it.packageName == packageName }
    }

    /**
     * Gets all package names with active notifications.
     * Used to display notification dots on shortcuts.
     *
     * @return Set of package names with notifications
     */
    fun getPackagesWithNotifications(): Set<String> {
        return activeNotifications.map { it.packageName }.toSet()
    }
}
