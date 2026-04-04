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
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
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
 *
 * Handles:
 * - Window insets and keyboard detection
 * - Colors (background, text, images)
 * - Fonts and text styles
 * - Visibility and alignment
 * - Sizes and spacing
 * - Fragment navigation
 */
class UIUtils(private val context: Context) {

    private val sharedPreferenceManager = SharedPreferenceManager(context)

    // ============================================
    // Window Insets
    // ============================================

    /**
     * Applies system bar insets to the view.
     * Ensures content isn't drawn under status/navigation bars.
     *
     * @param view Root view to apply insets to
     */
    fun adjustInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // Get system bars and display cutout insets
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            // Apply padding to all sides
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Replicates adjustResize behavior for SDK 35+.
     * The standard adjustResize doesn't work with SDK 35's new keyboard behavior.
     * Manually adjusts layout height based on keyboard visibility.
     *
     * @param view Root view to monitor
     */
    fun setLayoutListener(view: View) {
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var lastKeyboardState = false

            override fun onPreDraw(): Boolean {
                val rect = Rect()
                view.getWindowVisibleDisplayFrame(rect)

                val screenHeight = view.rootView.height
                val keyboardHeight = screenHeight - rect.bottom

                // Keyboard is considered visible if it takes >15% of screen height
                val isKeyboardVisible = keyboardHeight > screenHeight * 0.15

                // Only respond to state changes to avoid excessive layout passes
                if (isKeyboardVisible != lastKeyboardState) {
                    lastKeyboardState = isKeyboardVisible

                    if (isKeyboardVisible) {
                        // Shrink layout to fit above keyboard
                        val availableHeight = screenHeight - keyboardHeight
                        view.layoutParams.height = availableHeight
                    } else {
                        // Restore full height when keyboard hidden
                        view.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
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
     *
     * @param window Window to style
     * @param applyDarkening Whether to apply dark overlay for transparent backgrounds
     */
    fun setBackground(window: Window, applyDarkening: Boolean = false) {
        val bgColor = sharedPreferenceManager.getBgColor()
        val finalColor = if (applyDarkening && bgColor == Color.parseColor("#00000000")) {
            Color.parseColor("#3F000000")
        } else {
            bgColor
        }
        window.decorView.setBackgroundColor(finalColor)
    }

    /**
     * Applies text color filter to an ImageView.
     * Used to tint icons based on user's text color preference.
     *
     * @param view ImageView to tint
     */
    fun setImageColor(view: ImageView) {
        view.setColorFilter(sharedPreferenceManager.getTextColor())
    }

    /**
     * Recursively applies text colors to a view and its children.
     * Handles TextViews with their compound drawables.
     *
     * @param view Root view to style
     */
    fun setTextColors(view: View) {
        val color = sharedPreferenceManager.getTextColor()
        when {
            // Recursively process view groups
            view is ViewGroup -> {
                view.children.forEach { child ->
                    setTextColors(child)
                }
            }
            // Apply to TextViews
            hasMethod(view, "setTextColor") -> {
                val textView = view as TextView
                textView.setTextColor(color)

                // Apply color to compound drawables (icons next to text)
                val drawables = textView.compoundDrawables
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    drawables.getOrNull(0)?.colorFilter =
                        BlendModeColorFilter(sharedPreferenceManager.getTextColor(), BlendMode.SRC_ATOP)
                    drawables.getOrNull(2)?.colorFilter =
                        BlendModeColorFilter(sharedPreferenceManager.getTextColor(), BlendMode.SRC_ATOP)
                } else {
                    drawables.getOrNull(0)?.colorFilter =
                        PorterDuffColorFilter(sharedPreferenceManager.getTextColor(), PorterDuff.Mode.SRC_ATOP)
                    drawables.getOrNull(2)?.colorFilter =
                        PorterDuffColorFilter(sharedPreferenceManager.getTextColor(), PorterDuff.Mode.SRC_ATOP)
                }

                // Apply text shadow if enabled
                if (sharedPreferenceManager.isTextShadowEnabled()) {
                    textView.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                } else {
                    textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            }
            else -> {
                // For non-text views, set background color
                view.setBackgroundColor(color)
            }
        }
    }

    /**
     * Updates status bar appearance based on text color.
     * Switches between light and dark status bar icons.
     *
     * @param window Window to update
     */
    fun setStatusBarColor(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insetController = window.insetsController
            // Determine if status bar should be light or dark based on text color
            when (sharedPreferenceManager.getTextString()) {
                "#FFF3F3F3" -> insetController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "#FF0C0C0C" -> insetController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                "material" -> {
                    // Follow system dark mode setting
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
     * Checks if a view has a specific method using reflection.
     * Used to safely check for methods before calling them.
     *
     * @param view View to check
     * @param methodName Method name to look for
     * @return true if method exists
     */
    private fun hasMethod(view: View, methodName: String): Boolean {
        return try {
            view.javaClass.getMethod(methodName, Int::class.java)
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    /**
     * Sets colors for menu item TextViews (search bar, title).
     * Applies custom alpha for hint text and icons.
     *
     * @param view TextView to style
     * @param alphaHex Hex alpha value (default "FF" = fully opaque)
     */
    fun setMenuItemColors(view: TextView, alphaHex: String = "FF") {
        val color = sharedPreferenceManager.getTextColor()
        view.setTextColor(setAlpha(color, alphaHex))
        view.setHintTextColor(setAlpha(color, "A9"))

        val drawables = view.compoundDrawables
        // Apply color filter based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawables.getOrNull(0)?.mutate()?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
            drawables.getOrNull(2)?.mutate()?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            drawables.getOrNull(0)?.mutate()?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            drawables.getOrNull(2)?.mutate()?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        // Apply alpha to drawables
        drawables.getOrNull(0)?.alpha = "A9".toInt(16)
        drawables.getOrNull(2)?.alpha = "A9".toInt(16)

        // Apply text shadow if enabled
        if (sharedPreferenceManager.isTextShadowEnabled()) {
            view.setShadowLayer(4f, 2f, 2f, Color.BLACK)
        } else {
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    /**
     * Recursively applies font to all TextViews in a view hierarchy.
     *
     * @param view Root view to process
     */
    fun setTextFont(view: View) {

        when {
            view is ViewGroup -> {
                view.children.forEach { child ->
                    setTextFont(child)
                }
            }
            hasMethod(view, "setTextAppearance") -> {
                setFont(view as TextView)
            }
        }
    }

    /**
     * Sets font and style for a specific TextView.
     * Supports system fonts, custom fonts, and style variations.
     *
     * @param view TextView to style
     */
    fun setFont(view: TextView) {
        var font = sharedPreferenceManager.getTextFont()
        val style = sharedPreferenceManager.getTextStyle()

        // Resolve font based on preference
        val newFont = when (font) {
            "system" -> {
                // Get system default font
                val typedArray = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault, intArrayOf(android.R.attr.fontFamily))
                val systemFont = typedArray.getString(0)
                typedArray.recycle()
                if (systemFont != null) {
                    Typeface.create(systemFont, Typeface.NORMAL)
                } else {
                    Typeface.DEFAULT
                }
            }
            "casual" -> Typeface.SANS_SERIF
            "cursive" -> Typeface.SANS_SERIF // Cursive may not be available on all devices
            "monospace" -> Typeface.MONOSPACE
            "sans-serif" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            // System-defined variant fonts
            "sans-serif-light", "sans-serif-thin", "sans-serif-condensed", "sans-serif-condensed-light", "sans-serif-smallcaps" -> {
                Typeface.create(font, Typeface.NORMAL)
            }
            else -> {
                // Custom fonts from FontMap
                val fontId = FontMap.fonts[font]
                if (fontId != null) {
                    ResourcesCompat.getFont(context, fontId)
                } else {
                    Typeface.DEFAULT
                }
            }
        }

        // Apply style (normal, bold, italic, bold-italic)
        when (style) {
            "normal" -> {
                view.typeface = newFont
            }
            "bold" -> {
                view.typeface = Typeface.create(newFont, Typeface.BOLD)
            }
            "italic" -> {
                view.typeface = Typeface.create(newFont, Typeface.ITALIC)
            }
            "bold-italic" -> {
                view.typeface = Typeface.create(newFont, Typeface.BOLD_ITALIC)
            }
        }
    }

    /**
     * Modifies alpha channel of a color.
     *
     * @param color Original color
     * @param alphaHex Hex alpha value (00-FF)
     * @return Color with new alpha
     */
    private fun setAlpha(color: Int, alphaHex: String): Int {
        val newAlpha = Integer.parseInt(alphaHex, 16)

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return Color.argb(newAlpha, r, g, b)
    }

    // ============================================
    // Visibility Methods
    // ============================================

    /**
     * Sets clock visibility based on preference.
     * Uses minimal height instead of GONE to preserve layout space.
     *
     * @param clock TextClock to update
     */
    fun setClockVisibility(clock: TextClock) {
        val layoutParams = clock.layoutParams

        if (sharedPreferenceManager.isClockEnabled()) {
            layoutParams.height = WRAP_CONTENT
        } else {
            layoutParams.height = 1 // Minimal height instead of hiding completely
        }
        clock.layoutParams = layoutParams
    }

    /**
     * Sets date text visibility based on preference.
     *
     * @param dateText TextClock to update
     */
    fun setDateVisibility(dateText: TextClock) {
        dateText.visibility = when (sharedPreferenceManager.isDateEnabled()) {
            true -> {
                View.VISIBLE
            }
            false -> {
                View.GONE
            }
        }
    }

    /**
     * Sets search bar and layout visibility based on preferences.
     *
     * @param searchView Search input field
     * @param searchLayout Layout containing search UI
     * @param replacementView Placeholder when search is hidden
     */
    fun setSearchVisibility(searchView: View, searchLayout: View, replacementView: View) {
        setSearchLayoutVisibility(searchLayout, replacementView)
        if (sharedPreferenceManager.isSearchEnabled()) {
            searchView.visibility = View.VISIBLE
        } else {
            searchView.visibility = View.GONE
        }
    }

    /**
     * Sets contacts toggle visibility.
     *
     * @param contactsView Contacts toggle ImageView
     * @param searchLayout Search layout containing the toggle
     * @param replacementView Placeholder when contacts are hidden
     */
    fun setContactsVisibility(contactsView: View, searchLayout: View, replacementView: View) {
        setSearchLayoutVisibility(searchLayout, replacementView)
        if (sharedPreferenceManager.areContactsEnabled()) {
            contactsView.visibility = View.VISIBLE
        } else {
            contactsView.visibility = View.GONE
        }
    }

    /**
     * Sets web search button visibility.
     *
     * @param webSearchButton Web search ImageView
     */
    fun setWebSearchVisibility(webSearchButton: View) {
        if (sharedPreferenceManager.isWebSearchEnabled()) {
            webSearchButton.visibility = View.VISIBLE
        } else {
            webSearchButton.visibility = View.GONE
        }
    }

    /**
     * Helper to manage search layout and replacement visibility.
     * Shows replacement when both search and contacts are disabled.
     *
     * @param searchLayout Search layout container
     * @param replacementView Placeholder view
     */
    private fun setSearchLayoutVisibility(searchLayout: View, replacementView: View) {
        if (!sharedPreferenceManager.isSearchEnabled() && !sharedPreferenceManager.areContactsEnabled()) {
            searchLayout.visibility = View.GONE
            replacementView.visibility = View.VISIBLE
        } else {
            replacementView.visibility = View.GONE
            searchLayout.visibility = View.VISIBLE
        }
    }

    // ============================================
    // Alignment Methods
    // ============================================

    /**
     * Sets alignment for clock and date TextClocks.
     *
     * @param clock Clock TextClock
     * @param dateText Date TextClock
     */
    fun setClockAlignment(clock: TextClock, dateText: TextClock) {
        val alignment = sharedPreferenceManager.getClockAlignment()
        setTextAlignment(clock, alignment)
        setTextAlignment(dateText, alignment)
    }

    /**
     * Sets alignment for all shortcuts in the home view.
     *
     * @param shortcuts LinearLayout containing shortcut TextViews
     */
    fun setShortcutsAlignment(shortcuts: LinearLayout) {
        val alignment = sharedPreferenceManager.getShortcutAlignment()
        shortcuts.children.forEach {
            if (it is TextView) {
                setTextGravity(it, alignment)
                setDrawables(it, alignment)
            }
        }
    }

    /**
     * Sets vertical alignment for shortcuts (top, center, bottom).
     * Adjusts weight of spacing views above and below shortcuts.
     *
     * @param topSpace Space above shortcuts
     * @param bottomSpace Space below shortcuts
     */
    fun setShortcutsVAlignment(topSpace: Space, bottomSpace: Space) {
        val alignment = sharedPreferenceManager.getShortcutVAlignment()
        val topLayoutParams = topSpace.layoutParams as LinearLayout.LayoutParams
        val bottomLayoutParams = bottomSpace.layoutParams as LinearLayout.LayoutParams

        // Different weight ratios for each alignment
        when (alignment) {
            "top" -> {
                topLayoutParams.weight = 0.1F
                bottomLayoutParams.weight = 0.42F
            }
            "center" -> {
                topLayoutParams.weight = 0.22F
                bottomLayoutParams.weight = 0.3F
            }
            "bottom" -> {
                topLayoutParams.weight = 0.42F
                bottomLayoutParams.weight = 0.1F
            }
        }

        topSpace.layoutParams = topLayoutParams
        bottomSpace.layoutParams = bottomLayoutParams
    }

    /**
     * Sets position of drawable icons based on text alignment.
     * Icons move to opposite side of text for balanced appearance.
     *
     * @param textView TextView with drawables
     * @param alignment Text alignment (left, center, right)
     * @param alignments Available alignment options
     */
    fun setDrawables(textView: TextView, alignment: String?, alignments: Array<String> = arrayOf("left","center","right")) {
        try {
            val drawables = textView.compoundDrawables.filterNotNull()
            val firstDrawable = drawables.firstOrNull() ?: return

            // Position icon opposite to text alignment for visual balance
            when (alignment) {
                alignments[0] -> {
                    textView.setCompoundDrawablesWithIntrinsicBounds(
                        firstDrawable,
                        null,
                        null,
                        null
                    )
                }

                alignments[1] -> {
                    textView.setCompoundDrawablesWithIntrinsicBounds(
                        firstDrawable,
                        null,
                        firstDrawable,
                        null
                    )
                }

                alignments[2] -> {
                    textView.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        null,
                        firstDrawable,
                        null
                    )
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Sets alignment for app menu items.
     *
     * @param textView App name TextView
     * @param editText Edit field for renaming
     * @param regionText Region/country subtitle TextView
     */
    fun setAppAlignment(
        textView: TextView,
        editText: TextView? = null,
        regionText: TextView? = null,
    ) {
        val alignment = sharedPreferenceManager.getAppAlignment()
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

    /**
     * Sets search input alignment.
     *
     * @param searchView Search TextInputEditText
     */
    fun setSearchAlignment(searchView: TextInputEditText) {
        setTextAlignment(searchView, sharedPreferenceManager.getSearchAlignment())
    }

    /**
     * Sets alignment for menu title (during shortcut rename).
     * Drawable icon positioned opposite to text.
     *
     * @param menuTitle Title TextView
     */
    fun setMenuTitleAlignment(menuTitle: TextView) {
        val alignment = sharedPreferenceManager.getAppAlignment()
        setTextGravity(menuTitle, alignment)
        setDrawables(menuTitle, alignment, arrayOf("right","center","left"))
    }

    /**
     * Sets text alignment for a TextView.
     *
     * @param view TextView to update
     * @param alignment Alignment string (left, center, right)
     */
    private fun setTextAlignment(view: TextView, alignment: String?) {
        try {
            view.textAlignment = when (alignment) {
            "left" -> View.TEXT_ALIGNMENT_VIEW_START

            "center" -> View.TEXT_ALIGNMENT_CENTER

            "right" -> View.TEXT_ALIGNMENT_VIEW_END

            else -> View.TEXT_ALIGNMENT_VIEW_START
            }
        } catch (_: Exception) {}
    }

    /**
     * Sets gravity for a TextView.
     * Controls both horizontal and vertical positioning.
     *
     * @param view TextView to update
     * @param alignment Alignment string (left, center, right)
     */
    private fun setTextGravity(view: TextView, alignment: String?) {
        try {
            view.gravity = when (alignment) {
                "left" -> Gravity.CENTER_VERTICAL or Gravity.START

                "center" -> Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL

                "right" -> Gravity.CENTER_VERTICAL or Gravity.END

                else -> Gravity.CENTER_VERTICAL or Gravity.START
            }
        } catch (_: Exception) {}
    }

    // ============================================
    // Size Methods
    // ============================================

    /**
     * Sets clock text size based on preference.
     * Uses predefined size presets.
     *
     * @param clock TextClock to size
     */
    fun setClockSize(clock: TextClock) {
        setTextSize(clock, sharedPreferenceManager.getClockSize(), 48F, 58F, 70F, 78F, 82F, 84F)
    }

    /**
     * Sets date text size based on preference.
     *
     * @param dateText TextClock to size
     */
    fun setDateSize(dateText: TextClock) {
        setTextSize(dateText, sharedPreferenceManager.getDateSize(), 14F, 17F, 20F, 23F, 26F, 29F)
    }

    /**
     * Sets size for all shortcuts in home view.
     *
     * @param shortcuts LinearLayout containing shortcuts
     */
    fun setShortcutsSize(shortcuts: LinearLayout) {

        val size = sharedPreferenceManager.getShortcutSize()

        shortcuts.children.forEach {
            if (it is TextView) {
                setShortcutSize(it, size)
            }
        }
    }

    /**
     * Sets auto-sizing text for a shortcut.
     * Falls back to fixed size on older API levels.
     *
     * @param shortcut TextView to size
     * @param size Size preset string
     */
    private fun setShortcutSize(shortcut: TextView, size: String?) {
        try {
            val sizeConfig = getShortcutSizeConfig(size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                shortcut.setAutoSizeTextTypeUniformWithConfiguration(
                    5, // Minimum text size in SP
                    sizeConfig.first, // Maximum text size in SP
                    2, // Step size in SP
                    TypedValue.COMPLEX_UNIT_SP
                )
            } else {
                shortcut.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeConfig.second)
            }
        } catch(_: Exception) {}
    }

    /**
     * Maps size preset to configuration values.
     *
     * @param size Size preset string
     * @return Pair of (maxAutoSize, fallbackSize)
     */
    private fun getShortcutSizeConfig(size: String?): Pair<Int, Float> {
        // Auto-size maximum values for API 26+
        val autoSizeMax = mapOf(
            "tiny" to 20,
            "small" to 24,
            "medium" to 28,
            "large" to 32,
            "extra" to 36,
            "huge" to 40
        )
        // Fallback fixed sizes for older API levels
        val fallbackSize = mapOf(
            "tiny" to 14f,
            "small" to 18f,
            "medium" to 22f,
            "large" to 26f,
            "extra" to 30f,
            "huge" to 34f
        )
        return Pair(
            autoSizeMax[size] ?: 20,
            fallbackSize[size] ?: 14f
        )
    }

    /**
     * Sets size for app menu items.
     *
     * @param textView App name TextView
     * @param editText Edit field (uses same size)
     * @param regionText Region subtitle (smaller size)
     */
    fun setAppSize(
        textView: TextView,
        editText: TextInputEditText? = null,
        regionText: TextView? = null
    ) {
        val size = sharedPreferenceManager.getAppSize()
        setTextSize(textView, size, 21F, 24F, 27F, 30F, 33F, 36F)
        if (editText != null) {
            setTextSize(editText, size, 21F, 24F, 27F, 30F, 33F, 36F)
        }
        if (regionText != null) {
            setTextSize(regionText, size, 11F, 14F, 17F, 20F, 23F, 26F)
        }
    }

    /**
     * Sets search input text size.
     *
     * @param searchView TextInputEditText to size
     */
    fun setSearchSize(searchView: TextInputEditText) {
        setTextSize(searchView, sharedPreferenceManager.getSearchSize(), 18F, 21F, 25F, 27F, 30F, 33F)
    }

    /**
     * Sets menu title text size.
     *
     * @param menuTitle Title TextView to size
     */
    fun setMenuTitleSize(menuTitle: TextView) {
        setTextSize(menuTitle, sharedPreferenceManager.getAppSize(), 27F, 30F, 33F, 36F, 39F, 42F)
    }

    /**
     * Generic text size setter using preset mappings.
     * Maps size string to specific SP values.
     *
     * @param view TextView to size
     * @param size Size preset string
     * @param t,s,m,l,x,h Size values for tiny through huge
     */
    private fun setTextSize(view: TextView, size: String?, t: Float, s: Float, m: Float, l: Float, x: Float, h: Float) {
        try {
            val sizeMap = mapOf("tiny" to t, "small" to s, "medium" to m, "large" to l, "extra" to x, "huge" to h)
            view.textSize = sizeMap[size] ?: 0F
        } catch (_: Exception) {}
    }

    // ============================================
    // Spacing Methods
    // ============================================

    /**
     * Sets spacing between shortcuts in home view.
     * Adjusts layout weight for even distribution.
     *
     * @param shortcuts LinearLayout containing shortcuts
     */
    fun setShortcutsSpacing(shortcuts: LinearLayout) {
        val shortcutWeight = sharedPreferenceManager.getShortcutWeight()
        shortcuts.children.forEach {
            if (it is TextView) {
                setShortcutSpacing(it, shortcutWeight)
            }
        }
    }

    /**
     * Sets layout weight for a shortcut.
     *
     * @param shortcut TextView to adjust
     * @param shortcutWeight Weight value from preferences
     */
    private fun setShortcutSpacing(shortcut: TextView, shortcutWeight: Float?) {
        val layoutParams = shortcut.layoutParams as LinearLayout.LayoutParams

        if (shortcutWeight != null) {
            layoutParams.weight = shortcutWeight
        }

        shortcut.layoutParams = layoutParams
    }

    /**
     * Sets vertical padding for app menu items.
     *
     * @param item TextView to pad
     */
    fun setItemSpacing(item: TextView) {
        val spacing = sharedPreferenceManager.getAppSpacing()
        if (spacing != null) {
            val spacingPx = dpToPx(spacing)
            item.setPadding(item.paddingLeft, spacingPx, item.paddingRight, spacingPx)
        }
    }

    /**
     * Sets vertical padding for location/weather items.
     *
     * @param item ConstraintLayout to pad
     */
    fun setWeatherSpacing(item: ConstraintLayout) {
        val spacing = sharedPreferenceManager.getAppSpacing()
        if (spacing != null) {
            val spacingPx = dpToPx(spacing)
            item.setPadding(item.paddingLeft, spacingPx, item.paddingRight, spacingPx)
        }
    }

    /**
     * Converts DP to pixels using display density.
     *
     * @param dp Density-independent pixels
     * @return Actual pixel value
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // ============================================
    // Status Bar Methods
    // ============================================

    /**
     * Shows or hides status bar based on preference.
     * Uses appropriate API for Android version.
     *
     * @param window Window to update
     */
    fun setStatusBar(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsetsController = window.insetsController

            windowInsetsController?.let {
                if (sharedPreferenceManager.isBarVisible()) {
                    it.show(WindowInsets.Type.statusBars())
                }
                else {
                    it.hide(WindowInsets.Type.statusBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val decorView = window.decorView
            if (sharedPreferenceManager.isBarVisible()) {
                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            } else {
                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    // ============================================
    // Fragment Navigation
    // ============================================

    /**
     * Switches to a new fragment in the settings activity.
     * Adds to back stack for back navigation.
     *
     * @param activity Activity hosting the fragment
     * @param fragment Fragment to display
     */
    fun switchFragment(activity: FragmentActivity, fragment: Fragment) {
        activity.supportFragmentManager
            .beginTransaction()
            .replace(R.id.settingsLayout, fragment)
            .addToBackStack(null)
            .commit()
    }
}
