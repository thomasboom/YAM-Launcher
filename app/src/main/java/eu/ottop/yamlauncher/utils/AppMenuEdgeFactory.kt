package eu.ottop.yamlauncher.utils

import android.app.Activity
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView

/**
 * Custom EdgeEffectFactory that speeds up overscroll animations.
 *
 * Makes the app menu easier to exit by reducing the rubberband effect duration.
 * The 0.75x speed factor allows faster navigation to home screen.
 */
class AppMenuEdgeFactory(private val activity: Activity) : RecyclerView.EdgeEffectFactory() {

    /**
     * Creates a custom edge effect with accelerated animation.
     *
     * @param view The RecyclerView this effect is attached to
     * @param direction Which edge (TOP, BOTTOM, LEFT, RIGHT)
     * @return Custom AppMenuEdgeEffect with faster animations
     */
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
        return AppMenuEdgeEffect(activity)
    }

    /**
     * Custom edge effect that reduces animation duration.
     * Makes the app menu easier to exit by shortening the bounce-back effect.
     */
    inner class AppMenuEdgeEffect(activity: Activity) : EdgeEffect(activity) {

        // Speed factor for animation - 0.75x makes it 25% faster
        // This allows quicker navigation back to home screen
        private val animationSpeedFactor = 0.75f

        /**
         * Accelerates the absorption animation when overscroll completes.
         * Called when user releases while overscrolled.
         *
         * @param velocity Velocity of the fling in pixels per second
         */
        override fun onAbsorb(velocity: Int) {
            super.onAbsorb((velocity * animationSpeedFactor).toInt())
        }

        /**
         * Accelerates the pull animation during overscroll.
         * Makes the edge stretch feel more responsive.
         *
         * @param deltaDistance How far the user has pulled (0 to 1)
         * @param displacement Horizontal position of touch
         */
        override fun onPull(deltaDistance: Float, displacement: Float) {
            super.onPull(deltaDistance * animationSpeedFactor, displacement)
        }

        /**
         * Accelerates the distance calculation during pull.
         * Reduces the effective pull distance for faster response.
         *
         * @param deltaDistance Raw distance pulled
         * @param displacement Horizontal position of touch
         * @return Adjusted distance for edge effect calculation
         */
        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
            return super.onPullDistance(deltaDistance * animationSpeedFactor, displacement)
        }

    }
}
