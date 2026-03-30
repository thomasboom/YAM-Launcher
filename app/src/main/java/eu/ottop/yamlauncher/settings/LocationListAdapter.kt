package eu.ottop.yamlauncher.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.UIUtils

/**
 * RecyclerView adapter for displaying weather locations.
 * Shows location name with region/country information.
 */
class LocationListAdapter(
    private val context: Context,
    private var locations: MutableList<Map<String, String>>,
    private val itemClickListener: OnItemClickListener
) :
    RecyclerView.Adapter<LocationListAdapter.AppViewHolder>() {

    private val uiUtils = UIUtils(context)

    /**
     * Called when user selects a location.
     */
    interface OnItemClickListener {
        fun onItemClick(name: String?, latitude: String?, longitude: String?)
    }

    // ============================================
    // ViewHolder
    // ============================================

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listItem: ConstraintLayout = itemView.findViewById(R.id.locationPlace)
        val textView: TextView = listItem.findViewById(R.id.locationName)
        val regionText: TextView = listItem.findViewById(R.id.regionName)

        init {
            listItem.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= locations.size) return@setOnClickListener
                val locationEntry = locations.getOrNull(position) ?: return@setOnClickListener
                val name = locationEntry["name"]
                val latitude = locationEntry["latitude"]
                val longitude = locationEntry["longitude"]
                itemClickListener.onItemClick(name, latitude, longitude)
            }
        }
    }

    // ============================================
    // Adapter Methods
    // ============================================

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.location_item_layout, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        if (position >= locations.size) {
            return
        }
        val location = locations[position]

        // Apply styling
        uiUtils.setAppAlignment(holder.textView, null, holder.regionText)
        uiUtils.setAppSize(holder.textView, null, holder.regionText)
        uiUtils.setWeatherSpacing(holder.listItem)

        holder.textView.text = location["name"]
        // Format region with country
        holder.regionText.text = context.getString(R.string.region_text, location["region"], location["country"])

        holder.textView.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int {
        return locations.size
    }

    /**
     * Updates the location list.
     *
     * @param newApps New list of location maps
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateLocations(newApps: MutableList<Map<String, String>>) {
        locations = newApps
        notifyDataSetChanged()
    }
}
