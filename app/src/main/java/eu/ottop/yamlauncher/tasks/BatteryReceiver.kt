package eu.ottop.yamlauncher.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import eu.ottop.yamlauncher.utils.Logger

/**
 * BroadcastReceiver for battery status changes.
 * Forwards the current battery level to [onLevel] (e.g. "85%").
 */
class BatteryReceiver(private val onLevel: (String) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            // Get battery level and scale (e.g., 85%)
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                val batteryPct = level * 100 / scale.toFloat()
                onLevel("${batteryPct.toInt()}%")
            } else {
                context?.let { ctx -> Logger.getInstance(ctx).w("BatteryReceiver", "Failed to get battery level") }
            }
        }
    }

    companion object {
        /**
         * Registers the battery receiver with the context.
         *
         * @param context Context to register with
         * @param onLevel Callback receiving the formatted battery level
         * @return The created BatteryReceiver instance
         */
        fun register(context: Context, onLevel: (String) -> Unit): BatteryReceiver {
            val receiver = BatteryReceiver(onLevel)
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(receiver, filter)
            return receiver
        }
    }
}
