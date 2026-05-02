package eu.ottop.yamlauncher

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Typeface
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import eu.ottop.yamlauncher.databinding.ActivityMainBinding
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.utils.AppNameResolver
import eu.ottop.yamlauncher.utils.AppUtils
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.UIUtils

/**
 * RecyclerView adapter for displaying installed apps in the app menu.
 * Handles app launching, shortcut assignment, and renaming.
 *
 * Features:
 * - DiffUtil for efficient list updates
 * - Pinned apps display with special icons
 * - Work profile indicator
 * - Shortcut selection mode
 * - Accessibility actions
 */
class AppDiffCallback(
    private val oldList: List<Triple<LauncherActivityInfo, UserHandle, Int>>,
    private val newList: List<Triple<LauncherActivityInfo, UserHandle, Int>>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    /**
     * Checks if items represent the same app.
     * Uses component name and profile index for comparison.
     */
    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos].first.componentName == newList[newPos].first.componentName &&
               oldList[oldPos].third == newList[newPos].third
    }

    /**
     * Checks if item contents have changed.
     * Compares app labels for content changes.
     */
    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos].first.label == newList[newPos].first.label
    }
}

/**
 * Adapter for displaying apps in the app menu.
 * Supports multiple interaction modes:
 * - Normal: Click to launch, long-press for actions
 * - Shortcut selection: Click to assign to shortcut slot
 */
class AppMenuAdapter(
    private val activity: MainActivity,
    binding: ActivityMainBinding,
    private var apps: MutableList<Triple<LauncherActivityInfo, UserHandle, Int>>,
    private val itemClickListener: OnItemClickListener,
    private val shortcutListener: OnShortcutListener,
    private val itemLongClickListener: OnItemLongClickListener,
    launcherApps: LauncherApps
) :
    RecyclerView.Adapter<AppMenuAdapter.AppViewHolder>() {

    // When set, clicking an app assigns it to this shortcut slot
    var shortcutIndex: Int = 0
    var shortcutTextView: TextView? = null

    private val sharedPreferenceManager = SharedPreferenceManager(activity)
    private val uiUtils = UIUtils(activity)
    private val appUtils = AppUtils(activity, launcherApps)
    private val logger = Logger.getInstance(activity)

    private var cachedTextColor: Int = sharedPreferenceManager.getTextColor()
    private var cachedTextShadowEnabled: Boolean = sharedPreferenceManager.isTextShadowEnabled()
    private var cachedContactsEnabled: Boolean = sharedPreferenceManager.areContactsEnabled()
    private var cachedAlignment: String? = sharedPreferenceManager.getAppAlignment()
    private var cachedSize: String? = sharedPreferenceManager.getAppSize()
    private var cachedSpacing: Int? = sharedPreferenceManager.getAppSpacing()
    private var cachedTypeface: Typeface? = uiUtils.resolveTypeface()

    private val drawableEmpty = ResourcesCompat.getDrawable(activity.resources, R.drawable.ic_empty, null)
    private val drawablePinFilled = ResourcesCompat.getDrawable(activity.resources, R.drawable.keep_filled_15px, null)
    private val drawablePin = ResourcesCompat.getDrawable(activity.resources, R.drawable.keep_15px, null)
    private val drawableWork = ResourcesCompat.getDrawable(activity.resources, R.drawable.ic_work_app, null)

    fun onPreferencesChanged() {
        cachedTextColor = sharedPreferenceManager.getTextColor()
        cachedTextShadowEnabled = sharedPreferenceManager.isTextShadowEnabled()
        cachedContactsEnabled = sharedPreferenceManager.areContactsEnabled()
        cachedAlignment = sharedPreferenceManager.getAppAlignment()
        cachedSize = sharedPreferenceManager.getAppSize()
        cachedSpacing = sharedPreferenceManager.getAppSpacing()
        cachedTypeface = uiUtils.resolveTypeface()
        notifyDataSetChanged()
    }

    // ============================================
    // Listener Interfaces
    // ============================================

    /** Called when user clicks an app to launch it */
    interface OnItemClickListener {
        fun onItemClick(appInfo: LauncherActivityInfo, userHandle: UserHandle)
    }

    /** Called when user selects an app for a shortcut */
    interface OnShortcutListener {
        fun onShortcut(
            appInfo: LauncherActivityInfo,
            userHandle: UserHandle,
            textView: TextView,
            userProfile: Int,
            shortcutView: TextView,
            shortcutIndex: Int
        )
    }

    /** Called when user long-presses an app */
    interface OnItemLongClickListener {
        fun onItemLongClick(
            appInfo: LauncherActivityInfo,
            userHandle: UserHandle,
            userProfile: Int
        )
    }

    // ============================================
    // ViewHolder
    // ============================================

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Container for the list item
        val listItem: FrameLayout = itemView.findViewById(R.id.listItem)
        // App name display
        val textView: TextView = listItem.findViewById(R.id.appName)
        // Rename input layout (hidden by default)
        val editView: LinearLayout = listItem.findViewById(R.id.renameView)
        // Text field in rename layout
        val editText: TextInputEditText = editView.findViewById(R.id.appNameEdit)

        init {
            // Single tap behavior depends on mode
            textView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= apps.size) {
                    return@setOnClickListener
                }
                val entry = apps[position]
                val app = entry.first

                // If in shortcut selection mode, assign to shortcut instead of launching
                val localShortcut = shortcutTextView
                if (localShortcut != null) {
                    shortcutListener.onShortcut(app, entry.second, textView, entry.third, localShortcut, shortcutIndex)
                }
                else {
                    itemClickListener.onItemClick(app, entry.second)
                }
            }

            // Long press opens action menu or assigns shortcut
            textView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= apps.size) {
                    return@setOnLongClickListener true
                }

                val entry = apps[position]
                val app = entry.first

                // In shortcut mode, long press also assigns
                val localShortcut = shortcutTextView
                if (localShortcut != null) {
                    shortcutListener.onShortcut(app, entry.second, textView, entry.third, localShortcut, shortcutIndex)
                    return@setOnLongClickListener true
                } else {
                    // Normal mode: show action menu
                    itemLongClickListener.onItemLongClick(
                        app,
                        entry.second,
                        entry.third
                    )
                    return@setOnLongClickListener true
                }
            }
        }
    }

    // ============================================
    // Adapter Methods
    // ============================================

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_item_layout, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.editView.visibility = View.INVISIBLE
        if (position >= apps.size) {
            return
        }
        val app = apps[position]

        // Show pin icon for pinned apps
        if (sharedPreferenceManager.isAppPinned(app.first.componentName.flattenToString(), app.third)) {
            if (app.third != 0) {
                // Pinned work profile app
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(drawablePinFilled, null, drawableEmpty, null)
            }
            else {
                // Pinned personal profile app
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(drawablePin, null, drawableEmpty, null)
            }
            holder.textView.compoundDrawables.getOrNull(0)?.colorFilter = BlendModeColorFilter(cachedTextColor, BlendMode.SRC_ATOP)
        }
        // Show work profile icon for non-pinned work apps
        else if (app.third != 0) {
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(drawableWork, null, drawableEmpty, null)
            holder.textView.compoundDrawables.getOrNull(0)?.colorFilter =
                BlendModeColorFilter(cachedTextColor, BlendMode.SRC_ATOP)
        }
        // Empty drawable for personal profile non-pinned apps
        else {
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(drawableEmpty, null, drawableEmpty, null)
        }

        // Apply styling from preferences
        uiUtils.setAppAlignment(holder.textView, cachedAlignment, holder.editText)
        uiUtils.setAppSize(holder.textView, cachedSize, holder.editText)
        uiUtils.setItemSpacing(holder.textView, cachedSpacing)
        uiUtils.setTextFont(holder.listItem, cachedTypeface)
        holder.textView.setTextColor(cachedTextColor)
        if (cachedTextShadowEnabled) {
            holder.textView.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        } else {
            holder.textView.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
        }

        // Check if app is still installed (treats archived apps as installed)
        val isAppInstalled = appUtils.isAppInstalled(
            app.first.applicationInfo.packageName,
            app.third
        )

        // Set app name or removal placeholder
        if (isAppInstalled) {
            holder.textView.text = sharedPreferenceManager.getAppName(
                app.first.componentName.flattenToString(),
                app.third,
                AppNameResolver.resolveBaseLabel(activity, app.first)
            )
            // Pre-fill edit text for rename mode
            holder.editText.setText(holder.textView.text)
        }
        else {
            holder.textView.text = activity.getString(R.string.removing)
        }

        holder.textView.visibility = View.VISIBLE

        // Accessibility actions
        ViewCompat.addAccessibilityAction(holder.textView, activity.getString(R.string.close_app_menu)) { _, _ ->
            activity.backToHome()
            true
        }

        if (cachedContactsEnabled) {
            ViewCompat.addAccessibilityAction(holder.textView, activity.getString(R.string.switch_to_contacts)) { _, _ ->
                activity.switchMenus()
                true
            }
        }
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    /**
     * Updates app list with DiffUtil for efficient animations.
     * Preserves RecyclerView position when possible.
     *
     * @param newApps New list of apps
     */
    fun updateApps(newApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        val diffCallback = AppDiffCallback(apps, newApps)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        apps = newApps.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Replaces app list without diff calculation.
     * Use for search results where positions change significantly.
     *
     * @param newApps New list of apps
     */
    @SuppressLint("NotifyDataSetChanged")
    fun setApps(newApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }
}
