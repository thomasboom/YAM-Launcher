package eu.ottop.yamlauncher.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import eu.ottop.yamlauncher.settings.SharedPreferenceManager

/**
 * Animation utility class for UI transitions.
 * Handles home/app menu transitions and background color animations.
 *
 * Animation durations are configurable via SharedPreferences.
 */
class Animations (context: Context) {

    private val sharedPreferenceManager = SharedPreferenceManager(context)

    // Flag to prevent concurrent animations
    // Prevents multiple transitions from conflicting
    var isInAnim = false

    // ============================================
    // Public Animation Methods
    // ============================================

    /**
     * Fades a view in (for small UI elements like action menus).
     *
     * @param view The view to fade in
     */
    fun fadeViewIn(view: View) {
        view.fadeIn()
    }

    /**
     * Fades a view out (for small UI elements like action menus).
     *
     * @param view The view to fade out
     */
    fun fadeViewOut(view: View) {
        view.fadeOut()
    }

    /**
     * Animates transition from app menu back to home screen.
     * Slides app view down and fades home view in.
     *
     * @param homeView The home screen view to show
     * @param appView The app menu view to hide
     * @param duration Animation duration in milliseconds
     */
    fun showHome(homeView: View, appView: View, duration: Long) {
        appView.slideOutToBottom(duration)
        homeView.fadeIn(duration)
    }

    /**
     * Animates transition from home screen to app menu.
     * Slides app view up from bottom and fades home view out.
     *
     * @param homeView The home screen view to hide
     * @param appView The app menu view to show
     */
    fun showApps(homeView: View, appView: View) {
        isInAnim = true
        appView.slideInFromBottom()
        homeView.fadeOut()
    }

    /**
     * Animates semi-transparent overlay appearing on app menu open.
     * Only animates if background is fully transparent and darkening is enabled.
     */
    fun backgroundIn(activity: Activity) {
        val originalColor = sharedPreferenceManager.getBgColor()
        if (!shouldAnimateDim(originalColor)) return

        animateDim(activity, originalColor, DIM_COLOR, sharedPreferenceManager.getAnimationSpeed())
    }

    /**
     * Animates semi-transparent overlay disappearing on return to home.
     * If homescreen darkening is enabled, keeps the dark background.
     */
    fun backgroundOut(activity: Activity, duration: Long) {
        val originalColor = sharedPreferenceManager.getBgColor()
        if (!shouldAnimateDim(originalColor)) return
        if (!sharedPreferenceManager.isAppDrawerDarkeningEnabled()) return

        animateDim(activity, DIM_COLOR, originalColor, duration)
    }

    private fun shouldAnimateDim(originalColor: Int): Boolean {
        if (!sharedPreferenceManager.isAppDrawerDarkeningEnabled()) return false
        // Skip animation if both app drawer and homescreen dimming are enabled (smooth transition)
        val homeDimming = sharedPreferenceManager.isHomescreenDarkeningEnabled()
        return !(homeDimming && originalColor == TRANSPARENT)
    }

    private fun animateDim(activity: Activity, from: Int, to: Int, duration: Long) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            addUpdateListener { animator ->
                activity.window.decorView.setBackgroundColor(animator.animatedValue as Int)
            }
            this.duration = duration
            start()
        }
    }

    // ============================================
    // Private Animation Extensions
    // ============================================

    /**
     * Slides view in from bottom of screen.
     * Includes scale and alpha animation for polished entrance.
     */
    private fun View.slideInFromBottom() {
        if (isVisible) return
        // Start slightly offset and scaled
        translationY = height.toFloat() / 5
        scaleY = 1.2f
        alpha = 0f
        visibility = View.VISIBLE

        animate()
            .translationY(0f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(sharedPreferenceManager.getAnimationSpeed())
            .setListener(endCancelReset())
    }

    /**
     * Slides view out to bottom of screen.
     * Sets isInAnim flag to prevent concurrent animations.
     */
    private fun View.slideOutToBottom(duration: Long) {
        if (!isVisible) return
        isInAnim = true
        animate()
            .translationY(height.toFloat() / 5)
            .scaleY(1.2f)
            .alpha(0f)
            .setDuration(duration / 2)
            .setListener(endCancelReset(visibilityAfter = View.INVISIBLE))
    }

    /**
     * Fades view in with slight upward motion.
     * Uses configurable animation speed from preferences.
     */
    private fun View.fadeIn(duration: Long = sharedPreferenceManager.getAnimationSpeed()) {
        if (isVisible) return
        alpha = 0f
        translationY = -height.toFloat() / 100
        visibility = View.VISIBLE

        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setListener(null)
    }

    /**
     * Fades view out with slight upward motion.
     * Sets isInAnim flag to prevent concurrent animations.
     */
    private fun View.fadeOut() {
        if (!isVisible) return
        isInAnim = true
        animate()
            .alpha(0f)
            .translationY(-height.toFloat() / 100)
            .setDuration(sharedPreferenceManager.getAnimationSpeed() / 2)
            .setListener(endCancelReset(visibilityAfter = View.INVISIBLE))
    }

    private fun View.endCancelReset(visibilityAfter: Int = View.VISIBLE) =
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = visibilityAfter
                isInAnim = false
            }
            override fun onAnimationCancel(animation: Animator) {
                visibility = visibilityAfter
                isInAnim = false
            }
        }

    private companion object {
        val TRANSPARENT = "#00000000".toColorInt()
        val DIM_COLOR = "#3F000000".toColorInt()
    }
}
