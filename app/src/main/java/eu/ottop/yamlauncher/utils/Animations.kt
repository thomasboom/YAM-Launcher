package eu.ottop.yamlauncher.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
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
        appView.slideInFromBottom()
        homeView.fadeOut()
    }

    /**
     * Animates semi-transparent overlay appearing on app menu open.
     * Only animates if background is fully transparent.
     *
     * @param activity The activity to animate
     */
    fun backgroundIn(activity: Activity) {
        val originalColor = sharedPreferenceManager.getBgColor()

        // Only animate darkness onto the transparent background
        if (originalColor == Color.parseColor("#00000000")) {
            val newColor = Color.parseColor("#3F000000")

            val backgroundColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, newColor)
            backgroundColorAnimator.addUpdateListener { animator ->
                activity.window.decorView.setBackgroundColor(animator.animatedValue as Int)
            }

            val duration = sharedPreferenceManager.getAnimationSpeed()
            backgroundColorAnimator.duration = duration

            backgroundColorAnimator.start()
        } else {
            return
        }
    }

    /**
     * Animates semi-transparent overlay disappearing on return to home.
     * Only animates if background is fully transparent.
     *
     * @param activity The activity to animate
     * @param duration Animation duration in milliseconds
     */
    fun backgroundOut(activity: Activity, duration: Long) {
        val newColor = sharedPreferenceManager.getBgColor()

        // Only animate darkness onto the transparent background
        if (newColor == Color.parseColor("#00000000")) {
            val originalColor = Color.parseColor("#3F000000")

            val backgroundColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, newColor)
            backgroundColorAnimator.addUpdateListener { animator ->
                activity.window.decorView.setBackgroundColor(animator.animatedValue as Int)
            }

            backgroundColorAnimator.duration = duration

            backgroundColorAnimator.start()
        } else {
            return
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
        if (visibility != View.VISIBLE) {
            // Start slightly offset and scaled
            translationY = height.toFloat()/5
            scaleY = 1.2f
            alpha = 0f
            visibility = View.VISIBLE

            val duration = sharedPreferenceManager.getAnimationSpeed()

            animate()
                    .translationY(0f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(null)
        }
    }

    /**
     * Slides view out to bottom of screen.
     * Sets isInAnim flag to prevent concurrent animations.
     */
    private fun View.slideOutToBottom(duration: Long) {
        if (visibility == View.VISIBLE) {
            isInAnim = true
            animate()
                .translationY(height.toFloat() / 5)
                .scaleY(1.2f)
                .alpha(0f)
                .setDuration(duration/2)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        visibility = View.INVISIBLE
                        isInAnim = false
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        visibility = View.INVISIBLE
                        isInAnim = false
                    }
                })
        }
    }

    /**
     * Fades view in with slight upward motion.
     * Uses configurable animation speed from preferences.
     *
     * @param duration Animation duration (defaults to preference value)
     */
    private fun View.fadeIn(duration: Long = sharedPreferenceManager.getAnimationSpeed()) {
        if (visibility != View.VISIBLE) {
            alpha = 0f
            translationY = -height.toFloat()/100
            visibility = View.VISIBLE

            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setListener(null)
        }
    }

    /**
     * Fades view out with slight upward motion.
     * Sets isInAnim flag to prevent concurrent animations.
     */
    private fun View.fadeOut() {
        if (visibility == View.VISIBLE) {
            isInAnim = true
            val duration = sharedPreferenceManager.getAnimationSpeed()

            animate()
                .alpha(0f)
                .translationY(-height.toFloat()/100)
                .setDuration(duration/2)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        visibility = View.INVISIBLE
                        isInAnim = false
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        visibility = View.INVISIBLE
                        isInAnim = false
                    }
                })

        }
    }
}
