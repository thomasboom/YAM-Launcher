package eu.ottop.yamlauncher.tasks

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import eu.ottop.yamlauncher.utils.Logger

/**
 * AccessibilityService for locking the screen.
 * Used as an alternative to DeviceAdmin when more permissions aren't available.
 *
 * Note: Requires accessibility service to be enabled by user in system settings.
 * Can be triggered via double-tap gesture.
 */
class ScreenLockService : AccessibilityService() {

    private lateinit var logger: Logger

    override fun onServiceConnected() {
        super.onServiceConnected()
        logger = Logger.getInstance(this)
        logger.i("ScreenLockService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - we only need the service for locking
    }

    override fun onInterrupt() {
        logger.w("ScreenLockService", "Accessibility service interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::logger.isInitialized) {
            logger = Logger.getInstance(this)
        }

        // Check for lock action in intent
        if (intent != null && intent.action == "LOCK_SCREEN") {
            logger.i("ScreenLockService", "Lock screen action received")
            performLockScreen()
        }
        // Stop service after handling action
        stopSelf()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Locks the screen using accessibility global action.
     * This simulates pressing the power button to lock.
     */
    private fun performLockScreen() {
        val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (success) {
            logger.i("ScreenLockService", "Screen locked successfully")
        } else {
            logger.e("ScreenLockService", "Failed to lock screen")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::logger.isInitialized) {
            logger.i("ScreenLockService", "Accessibility service destroyed")
        }
    }
}
