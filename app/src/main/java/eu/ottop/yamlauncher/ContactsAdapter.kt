package eu.ottop.yamlauncher

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.utils.UIUtils

/**
 * RecyclerView adapter for displaying contacts in the app menu.
 * Allows quick access to contacts for calling or viewing details.
 *
 * Features:
 * - DiffUtil for efficient updates
 * - Shortcut assignment support
 * - Search filtering
 */
class ContactDiffCallback(
    private val oldList: List<Pair<String, Int>>,
    private val newList: List<Pair<String, Int>>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    /** Compares contacts by ID (unique identifier) */
    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos].second == newList[newPos].second
    }

    /** Compares full contact data */
    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos] == newList[newPos]
    }
}

/**
 * Adapter for displaying contacts in the app menu.
 * Supports both viewing contacts and assigning them to shortcuts.
 */
class ContactsAdapter(
    private val activity: MainActivity,
    private var contacts: MutableList<Pair<String, Int>>,
    private val contactClickListener: OnContactClickListener,
    private val contactShortcutListener: OnContactShortcutListener,
) :
    RecyclerView.Adapter<ContactsAdapter.AppViewHolder>() {

    // Shortcut mode when set
    var shortcutIndex: Int = 0
    var shortcutTextView: TextView? = null

    private val uiUtils = UIUtils(activity)
    private val sharedPreferenceManager = SharedPreferenceManager(activity)

    private var cachedTextColor: Int = sharedPreferenceManager.getTextColor()
    private var cachedTextShadowEnabled: Boolean = sharedPreferenceManager.isTextShadowEnabled()
    private var cachedContactsEnabled: Boolean = sharedPreferenceManager.areContactsEnabled()
    private var cachedAlignment: String? = sharedPreferenceManager.getAppAlignment()
    private var cachedSize: String? = sharedPreferenceManager.getAppSize()
    private var cachedSpacing: Int? = sharedPreferenceManager.getAppSpacing()
    private var cachedTypeface: Typeface? = uiUtils.resolveTypeface()

    private val drawableEmpty = ResourcesCompat.getDrawable(activity.resources, R.drawable.ic_empty, null)

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

    /** Called when user clicks a contact */
    interface OnContactClickListener {
        fun onContactClick(contactId: Int)
    }

    /** Called when user assigns contact to a shortcut */
    interface OnContactShortcutListener {
        fun onContactShortcut(contactId: Int, contactName: String, shortcutView: TextView, shortcutIndex: Int)
    }

    // ============================================
    // ViewHolder
    // ============================================

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listItem: FrameLayout = itemView.findViewById(R.id.listItem)
        val textView: TextView = listItem.findViewById(R.id.appName)

        init {
            textView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= contacts.size) {
                    return@setOnClickListener
                }

                // Check if in shortcut selection mode
                val localShortcutTextView = shortcutTextView
                if (localShortcutTextView != null) {
                    val contact = contacts.getOrNull(position) ?: return@setOnClickListener
                    contactShortcutListener.onContactShortcut(contact.second, contact.first, localShortcutTextView, shortcutIndex)
                } else {
                    val contact = contacts.getOrNull(position) ?: return@setOnClickListener
                    contactClickListener.onContactClick(contact.second)
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
        val contact = contacts[position]

        // Clear any app icons (contacts don't have them)
        holder.textView.setCompoundDrawablesWithIntrinsicBounds(drawableEmpty, null, drawableEmpty, null)

        // Apply styling from preferences
        uiUtils.setAppAlignment(holder.textView, cachedAlignment)
        uiUtils.setAppSize(holder.textView, cachedSize)
        uiUtils.setItemSpacing(holder.textView, cachedSpacing)
        uiUtils.setTextFont(holder.listItem, cachedTypeface)
        holder.textView.setTextColor(cachedTextColor)
        if (cachedTextShadowEnabled) {
            holder.textView.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        } else {
            holder.textView.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
        }

        holder.textView.text = contact.first
        holder.textView.visibility = View.VISIBLE

        // Accessibility actions
        ViewCompat.addAccessibilityAction(holder.textView, activity.getString(R.string.close_app_menu)) { _, _ ->
            activity.backToHome()
            true
        }

        if (cachedContactsEnabled) {
            ViewCompat.addAccessibilityAction(holder.textView, activity.getString(R.string.switch_to_apps)) { _, _ ->
                activity.switchMenus()
                true
            }
        }
    }

    override fun getItemCount(): Int {
        return contacts.size
    }

    /**
     * Updates contact list with DiffUtil.
     *
     * @param newContacts New list of (name, contactId) pairs
     */
    fun updateContacts(newContacts: List<Pair<String, Int>>) {
        val diffCallback = ContactDiffCallback(contacts, newContacts)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        contacts = newContacts.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }
}
