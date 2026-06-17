package eu.ottop.yamlauncher.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputEditText
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.settings.SharedPreferenceManager

/**
 * UI utility class for managing view styling and layout.
 * Centralizes all UI customization based on user preferences.
 */
class UIUtils(private val context: Context) {

    private val sharedPreferenceManager = SharedPreferenceManager(context)

    fun resolveTypeface(): Typeface? {
        val font = sharedPreferenceManager.getTextFont()
        val style = sharedPreferenceManager.getTextStyle()

        val base = when (font) {
            "system" -> {
                val typedArray = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault, intArrayOf(android.R.attr.fontFamily))
                val systemFont = typedArray.getString(0)
                typedArray.recycle()
                if (systemFont != null) Typeface.create(systemFont, Typeface.NORMAL) else Typeface.DEFAULT
            }
            "casual" -> Typeface.SANS_SERIF
            "cursive" -> Typeface.SANS_SERIF
            "monospace" -> Typeface.MONOSPACE
            "sans-serif" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "sans-serif-light", "sans-serif-thin", "sans-serif-condensed", "sans-serif-condensed-light", "sans-serif-smallcaps" ->
                Typeface.create(font, Typeface.NORMAL)
            else -> {
                val fontId = FontMap.fonts[font]
                if (fontId != null) ResourcesCompat.getFont(context, fontId) else Typeface.DEFAULT
            }
        }

        return when (style) {
            "bold" -> Typeface.create(base, Typeface.BOLD)
            "italic" -> Typeface.create(base, Typeface.ITALIC)
            "bold-italic" -> Typeface.create(base, Typeface.BOLD_ITALIC)
            else -> base
        }
    }

    // ============================================
    // Window Insets
    // ============================================

    /**
     * Applies system bar insets to the view.
     * Ensures content isn't drawn under status/navigation bars.
     */
    fun adjustInsets(view: View) {
        // Apply current insets immediately so the first layout pass already accounts
        // for status bar / navigation bar / cutout padding. This avoids an early
        // layout shift where the first frame is drawn edge-to-edge and then a second
        // pass pushes content in once insets arrive.
        applyInsets(view, ViewCompat.getRootWindowInsets(view))

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            applyInsets(v, insets)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun applyInsets(view: View, insets: WindowInsetsCompat?) {
        if (insets == null) return
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
    }

    /**
     * Replicates adjustResize behavior for SDK 35+.
     * The standard adjustResize doesn't work with SDK 35's new keyboard behavior.
     * Manually adjusts layout height based on keyboard visibility.
     */
    fun setLayoutListener(view: View) {
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var lastKeyboardState = false

            override fun onPreDraw(): Boolean {
                val rect = Rect()
                view.getWindowVisibleDisplayFrame(rect)

                val screenHeight = view.rootView.height
                val keyboardHeight = screenHeight - rect.bottom
                val isKeyboardVisible = keyboardHeight > screenHeight * 0.15

                if (isKeyboardVisible != lastKeyboardState) {
                    lastKeyboardState = isKeyboardVisible
                    view.layoutParams.height = if (isKeyboardVisible) {
                        screenHeight - keyboardHeight
                    } else {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    view.requestLayout()
                }
                return true
            }
        })
    }

    // ============================================
    // Color Methods
    // ============================================

    /**
     * Sets the window background color from preferences.
     * If background is fully transparent, applies dark overlay for settings panels.
     */
    fun setBackground(window: Window, applyDarkening: Boolean = false, applyHomescreenDarkening: Boolean = false) {
        val bgColor = sharedPreferenceManager.getBgColor()
        val finalColor = when {
            applyHomescreenDarkening && bgColor == TRANSPARENT && sharedPreferenceManager.isHomescreenDarkeningEnabled() -> DIM_COLOR
            applyDarkening && bgColor == TRANSPARENT && sharedPreferenceManager.isSettingsDarkeningEnabled() -> DIM_COLOR
            else -> bgColor
        }
        window.decorView.setBackgroundColor(finalColor)
    }

    /**
     * Applies text color filter to an ImageView.
     * Uses mutate() to avoid tinting shared drawable instances used elsewhere.
     */
    fun setImageColor(view: ImageView) {
        val drawable = view.drawable?.mutate() ?: return
        drawable.setTint(sharedPreferenceManager.getTextColor())
    }

    /**
     * Recursively applies text colors to a view and its children.
     * Handles TextViews with their compound drawables.
     */
    fun setTextColors(view: View) {
        val color = sharedPreferenceManager.getTextColor()
        val shadowEnabled = sharedPreferenceManager.isTextShadowEnabled()
        when {
            view is ViewGroup -> view.children.forEach { setTextColors(it) }
            view is TextView -> applyTextColor(view, color, shadowEnabled)
            else -> view.setBackgroundColor(color)
        }
    }

    private fun applyTextColor(textView: TextView, color: Int, shadowEnabled: Boolean) {
        textView.setTextColor(color)
        val drawables = textView.compoundDrawables
        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        drawables.getOrNull(0)?.mutate()?.colorFilter = filter
        drawables.getOrNull(2)?.mutate()?.colorFilter = filter

        if (shadowEnabled) {
            textView.setShadowLayer(SHADOW_RADIUS, SHADOW_DX, SHADOW_DY, Color.BLACK)
        } else {
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    /**
     * Updates status bar appearance based on text color.
     * Switches between light and dark status bar icons.
     */
    fun setStatusBarColor(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetController = window.insetsController
            // Determine if status bar should be light or dark based on text color
            when (sharedPreferenceManager.getTextString()) {
                "#FFF3F3F3" -> insetController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "#FF0C0C0C" -> insetController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "material" -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    when (currentNightMode) {
                        Configuration.UI_MODE_NIGHT_YES -> insetController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                        Configuration.UI_MODE_NIGHT_NO -> insetController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            when (sharedPreferenceManager.getTextString()) {
                "#FFF3F3F3" -> decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                "#FF0C0C0C" -> decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                "material" -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    when (currentNightMode) {
                        Configuration.UI_MODE_NIGHT_YES -> decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        Configuration.UI_MODE_NIGHT_NO -> decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
            }
        }
    }

    /**
     * Sets colors for menu item TextViews (search bar, title).
     * Applies custom alpha for hint text and icons.
     */
    fun setMenuItemColors(view: TextView, alphaHex: String = "FF") {
        val color = sharedPreferenceManager.getTextColor()
        view.setTextColor(setAlpha(color, alphaHex))
        view.setHintTextColor(setAlpha(color, HINT_ALPHA_HEX))

        val drawables = view.compoundDrawables
        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        val alpha = HINT_ALPHA_HEX.toInt(16)
        drawables.getOrNull(0)?.mutate()?.apply { colorFilter = filter; this.alpha = alpha }
        drawables.getOrNull(2)?.mutate()?.apply { colorFilter = filter; this.alpha = alpha }

        if (sharedPreferenceManager.isTextShadowEnabled()) {
            view.setShadowLayer(SHADOW_RADIUS, SHADOW_DX, SHADOW_DY, Color.BLACK)
        } else {
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    /**
     * Recursively applies font to all TextViews in a view hierarchy.
     */
    fun setTextFont(view: View, typeface: Typeface?) {
        when (view) {
            is ViewGroup -> view.children.forEach { setTextFont(it, typeface) }
            is TextView -> view.typeface = typeface
        }
    }

    fun setFont(view: TextView, typeface: Typeface?) {
        view.typeface = typeface
    }

    /**
     * Modifies alpha channel of a color.
     */
    private fun setAlpha(color: Int, alphaHex: String): Int {
        val newAlpha = Integer.parseInt(alphaHex, 16)
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ============================================
    // Visibility Methods
    // ============================================

    /**
     * Sets clock visibility based on preference.
     * Uses minimal height instead of GONE to preserve layout space.
     */
    fun setClockVisibility(clock: TextClock) {
        clock.layoutParams = clock.layoutParams.apply {
            height = if (sharedPreferenceManager.isClockEnabled()) WRAP_CONTENT else 1
        }
    }

    fun setDateVisibility(dateText: TextClock) {
        dateText.visibility = if (sharedPreferenceManager.isDateEnabled()) View.VISIBLE else View.GONE
    }

    fun setSearchVisibility(searchView: View, searchLayout: View, replacementView: View) {
        setSearchLayoutVisibility(searchLayout, replacementView)
        searchView.visibility = if (sharedPreferenceManager.isSearchEnabled()) View.VISIBLE else View.GONE
    }

    fun setContactsVisibility(contactsView: View, searchLayout: View, replacementView: View) {
        setSearchLayoutVisibility(searchLayout, replacementView)
        contactsView.visibility = if (sharedPreferenceManager.areContactsEnabled()) View.VISIBLE else View.GONE
    }

    fun setWebSearchVisibility(webSearchButton: View) {
        webSearchButton.visibility = if (sharedPreferenceManager.isWebSearchEnabled()) View.VISIBLE else View.GONE
    }

    private fun setSearchLayoutVisibility(searchLayout: View, replacementView: View) {
        val showReplacement = !sharedPreferenceManager.isSearchEnabled() && !sharedPreferenceManager.areContactsEnabled()
        replacementView.visibility = if (showReplacement) View.VISIBLE else View.GONE
        searchLayout.visibility = if (showReplacement) View.GONE else View.VISIBLE
    }

    // ============================================
    // Alignment Methods
    // ============================================

    fun setClockAlignment(clock: TextClock, dateText: TextClock) {
        val alignment = sharedPreferenceManager.getClockAlignment()
        setTextAlignment(clock, alignment)
        setTextAlignment(dateText, alignment)
    }

    fun setShortcutsAlignment(shortcuts: LinearLayout) {
        val alignment = sharedPreferenceManager.getShortcutAlignment()
        shortcuts.children.forEach {
            if (it is TextView) {
                setTextGravity(it, alignment)
                setDrawables(it, alignment)
            }
        }
    }

    fun setShortcutsVAlignment(topSpace: Space, bottomSpace: Space) {
        val (topWeight, bottomWeight) = when (sharedPreferenceManager.getShortcutVAlignment()) {
            "top" -> 0.1F to 0.42F
            "bottom" -> 0.42F to 0.1F
            else -> 0.22F to 0.3F
        }
        topSpace.layoutParams = (topSpace.layoutParams as LinearLayout.LayoutParams).apply { weight = topWeight }
        bottomSpace.layoutParams = (bottomSpace.layoutParams as LinearLayout.LayoutParams).apply { weight = bottomWeight }
    }

    fun setDrawables(textView: TextView, alignment: String?, alignments: Array<String> = arrayOf("left","center","right")) {
        val firstDrawable = textView.compoundDrawables.filterNotNull().firstOrNull() ?: return
        // Place icon on the opposite side of the text for visual balance.
        val (left, right) = when (alignment) {
            alignments[1] -> firstDrawable to firstDrawable
            alignments[2] -> null to firstDrawable
            else -> firstDrawable to null
        }
        textView.setCompoundDrawablesWithIntrinsicBounds(left, null, right, null)
    }

    fun setAppAlignment(
        textView: TextView,
        alignment: String?,
        editText: TextView? = null,
        regionText: TextView? = null,
    ) {
        setTextGravity(textView, alignment)

        if (regionText != null) {
            setTextGravity(regionText, alignment)
            return
        }

        if (editText != null) {
            setDrawables(textView, alignment)
            setTextGravity(editText, alignment)
        }
    }

    fun setSearchAlignment(searchView: TextInputEditText) {
        setTextAlignment(searchView, sharedPreferenceManager.getSearchAlignment())
    }

    fun setMenuTitleAlignment(menuTitle: TextView) {
        val alignment = sharedPreferenceManager.getAppAlignment()
        setTextGravity(menuTitle, alignment)
        setDrawables(menuTitle, alignment, arrayOf("right","center","left"))
    }

    private fun setTextAlignment(view: TextView, alignment: String?) {
        view.textAlignment = when (alignment) {
            "center" -> View.TEXT_ALIGNMENT_CENTER
            "right" -> View.TEXT_ALIGNMENT_VIEW_END
            else -> View.TEXT_ALIGNMENT_VIEW_START
        }
    }

    private fun setTextGravity(view: TextView, alignment: String?) {
        view.gravity = when (alignment) {
            "center" -> Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.CENTER_VERTICAL or Gravity.END
            else -> Gravity.CENTER_VERTICAL or Gravity.START
        }
    }

    // ============================================
    // Size Methods
    // ============================================

    fun setClockSize(clock: TextClock) {
        setTextSize(clock, sharedPreferenceManager.getClockSize(), CLOCK_SIZES)
    }

    fun setDateSize(dateText: TextClock) {
        setTextSize(dateText, sharedPreferenceManager.getDateSize(), DATE_SIZES)
    }

    fun setShortcutsSize(shortcuts: LinearLayout) {
        val size = sharedPreferenceManager.getShortcutSize()
        shortcuts.children.forEach {
            if (it is TextView) setShortcutSize(it, size)
        }
    }

    private fun setShortcutSize(shortcut: TextView, size: String?) {
        val (maxAuto, fallback) = SHORTCUT_SIZE_CONFIGS[size] ?: SHORTCUT_DEFAULT_SIZE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            shortcut.setAutoSizeTextTypeUniformWithConfiguration(
                AUTOSIZE_MIN, maxAuto, AUTOSIZE_STEP, TypedValue.COMPLEX_UNIT_SP
            )
        } else {
            shortcut.setTextSize(TypedValue.COMPLEX_UNIT_SP, fallback)
        }
    }

    fun setAppSize(
        textView: TextView,
        size: String?,
        editText: TextInputEditText? = null,
        regionText: TextView? = null
    ) {
        setTextSize(textView, size, APP_SIZES)
        editText?.let { setTextSize(it, size, APP_SIZES) }
        regionText?.let { setTextSize(it, size, REGION_SIZES) }
    }

    fun setSearchSize(searchView: TextInputEditText) {
        setTextSize(searchView, sharedPreferenceManager.getSearchSize(), SEARCH_SIZES)
    }

    fun setMenuTitleSize(menuTitle: TextView) {
        setTextSize(menuTitle, sharedPreferenceManager.getAppSize(), MENU_TITLE_SIZES)
    }

    private fun setTextSize(view: TextView, size: String?, sizes: FloatArray) {
        view.textSize = sizes[sizeIndex(size)]
    }

    private fun sizeIndex(size: String?): Int = when (size) {
        "tiny" -> 0
        "small" -> 1
        "medium" -> 2
        "large" -> 3
        "extra" -> 4
        "huge" -> 5
        else -> 0
    }

    // ============================================
    // Spacing Methods
    // ============================================

    fun setShortcutsSpacing(shortcuts: LinearLayout) {
        val shortcutWeight = sharedPreferenceManager.getShortcutWeight()
        shortcuts.children.forEach {
            if (it is TextView) {
                it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = shortcutWeight
                }
            }
        }
    }

    fun setItemSpacing(item: TextView, spacing: Int?) {
        if (spacing == null) return
        val spacingPx = dpToPx(spacing)
        item.setPadding(item.paddingLeft, spacingPx, item.paddingRight, spacingPx)
    }

    fun setWeatherSpacing(item: ConstraintLayout) {
        val spacingPx = dpToPx(sharedPreferenceManager.getAppSpacing())
        item.setPadding(item.paddingLeft, spacingPx, item.paddingRight, spacingPx)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    // ============================================
    // Status Bar Methods
    // ============================================

    fun setStatusBar(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsetsController = window.insetsController
            windowInsetsController?.let {
                if (sharedPreferenceManager.isBarVisible()) {
                    it.show(WindowInsets.Type.statusBars())
                } else {
                    it.hide(WindowInsets.Type.statusBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            decorView.systemUiVisibility = if (sharedPreferenceManager.isBarVisible()) {
                decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            } else {
                decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    // ============================================
    // Fragment Navigation
    // ============================================

    fun switchFragment(activity: FragmentActivity, fragment: Fragment) {
        activity.supportFragmentManager
            .beginTransaction()
            .replace(R.id.settingsLayout, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        const val HINT_ALPHA_HEX = "A9"
        const val SHADOW_RADIUS = 4f
        const val SHADOW_DX = 2f
        const val SHADOW_DY = 2f
        const val AUTOSIZE_MIN = 5
        const val AUTOSIZE_STEP = 2

        val TRANSPARENT = "#00000000".toColorInt()
        val DIM_COLOR = "#3F000000".toColorInt()

        val CLOCK_SIZES = floatArrayOf(48F, 58F, 70F, 78F, 82F, 84F)
        val DATE_SIZES = floatArrayOf(14F, 17F, 20F, 23F, 26F, 29F)
        val APP_SIZES = floatArrayOf(21F, 24F, 27F, 30F, 33F, 36F)
        val REGION_SIZES = floatArrayOf(11F, 14F, 17F, 20F, 23F, 26F)
        val SEARCH_SIZES = floatArrayOf(18F, 21F, 25F, 27F, 30F, 33F)
        val MENU_TITLE_SIZES = floatArrayOf(27F, 30F, 33F, 36F, 39F, 42F)

        val SHORTCUT_SIZE_CONFIGS: Map<String, Pair<Int, Float>> = mapOf(
            "tiny" to (20 to 14f),
            "small" to (24 to 18f),
            "medium" to (28 to 22f),
            "large" to (32 to 26f),
            "extra" to (36 to 30f),
            "huge" to (40 to 34f),
        )
        val SHORTCUT_DEFAULT_SIZE = 20 to 14f
    }
}
