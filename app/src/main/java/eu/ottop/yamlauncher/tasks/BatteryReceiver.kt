package eu.ottop.yamlauncher.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import eu.ottop.yamlauncher.MainActivity
import eu.ottop.yamlauncher.utils.Logger
import java.lang.ref.WeakReference

/**
 * BroadcastReceiver for battery status changes.
 * Updates the home screen with current battery level.
 *
 * Uses WeakReference to MainActivity to prevent memory leaks.
 */
class BatteryReceiver(activity: MainActivity) : BroadcastReceiver() {

    private val logger: Logger
    private val activityRef: WeakReference<MainActivity>

    init {
        logger = Logger.getInstance(activity)
        activityRef = WeakReference(activity)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val activity = activityRef.get()
        if (activity == null) {
            logger.w("BatteryReceiver", "Activity is null, skipping battery update")
            return
        }

        intent?.let {
            // Get battery level and scale (e.g., 85%)
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                val batteryPct = level * 100 / scale.toFloat()
                // Update date bar with battery percentage (index 3)
                activity.modifyDate("${batteryPct.toInt()}%", 3)
            } else {
                logger.w("BatteryReceiver", "Failed to get battery level")
            }
        }
    }

    companion object {
        /**
         * Registers the battery receiver with the context.
         *
         * @param context Context to register with
         * @param activity MainActivity to update
         * @return The created BatteryReceiver instance
         */
        fun register(context: Context, activity: MainActivity): BatteryReceiver {
            val receiver = BatteryReceiver(activity)
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(receiver, filter)
            return receiver
        }
    }
}
