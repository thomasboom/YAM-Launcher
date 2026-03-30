package eu.ottop.yamlauncher.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.AppNameResolver
import eu.ottop.yamlauncher.utils.UIUtils

/**
 * RecyclerView adapter for displaying hidden apps.
 * Used in settings to allow users to unhide apps.
 */
class HiddenAppsAdapter(
    private val context: Context,
    private var apps: MutableList<Triple<LauncherActivityInfo, UserHandle, Int>>,
    private val itemClickListener: OnItemClickListener
) :
    RecyclerView.Adapter<HiddenAppsAdapter.AppViewHolder>() {

    private val sharedPreferenceManager = SharedPreferenceManager(context)
    private val uiUtils = UIUtils(context)

    /**
     * Called when user clicks to unhide an app.
     */
    interface OnItemClickListener {
        fun onItemClick(appInfo: LauncherActivityInfo, profile: Int)
    }

    // ============================================
    // ViewHolder
    // ============================================

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val listItem: FrameLayout = itemView.findViewById(R.id.listItem)
        val textView: TextView = listItem.findViewById(R.id.appName)

        init {
            textView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= apps.size) {
                    return@setOnClickListener
                }
                val appEntry = apps.getOrNull(position) ?: return@setOnClickListener
                itemClickListener.onItemClick(appEntry.first, appEntry.third)
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
        if (position >= apps.size) {
            return
        }
        val app = apps[position]

        // Show work profile indicator for work apps
        if (app.third != 0) {
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources,
                R.drawable.ic_work_app, null),null,null,null)
        }
        else {
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources,
                R.drawable.ic_empty, null),null,null,null)
        }

        // Apply styling
        uiUtils.setAppAlignment(holder.textView)
        uiUtils.setAppSize(holder.textView)
        uiUtils.setItemSpacing(holder.textView)

        // Get app name (may have been renamed)
        holder.textView.text = sharedPreferenceManager.getAppName(
            app.first.componentName.flattenToString(),
            app.third,
            AppNameResolver.resolveBaseLabel(context, app.first)
        )

        holder.textView.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    /**
     * Updates the hidden apps list.
     *
     * @param newApps New list of hidden apps
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateApps(newApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }
}
