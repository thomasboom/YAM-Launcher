package eu.ottop.yamlauncher.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.ottop.yamlauncher.R

/**
 * Custom preference that displays as a spinner dropdown.
 * Allows users to select from predefined options.
 *
 * XML Attributes:
 * - android:entries - Display labels for options
 * - android:entryValues - Internal values for options
 * - android:defaultValue - Default selection
 */
class SpinnerPreference (context: Context, attrs: AttributeSet? = null): Preference(context, attrs) {

    private var entries: Array<CharSequence>? = null
    private var entryValues: Array<CharSequence>? = null
    private var currentValue: String? = null
    private var defaultNo: String? = null
    private var spinner: Spinner? = null

    init {
        // Use custom layout for spinner preference
        widgetLayoutResource = R.layout.preference_spinner

        // Read custom attributes from XML
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SpinnerPreference,
            0, 0).apply {

            try {
                // Labels shown to user
                entries = getTextArray(R.styleable.SpinnerPreference_android_entries)
                // Internal values
                entryValues = getTextArray(R.styleable.SpinnerPreference_android_entryValues)
                // Default value key
                defaultNo = getString(R.styleable.SpinnerPreference_android_defaultValue)
            } finally {
                recycle()
            }
        }}

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        spinner = holder.findViewById(R.id.preferenceOptions) as Spinner

        // Set up adapter with entries
        if (entries != null) {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, entries!!)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner?.adapter = adapter
        }

        // Set current selection
        val selectedIndex = entryValues?.indexOf(currentValue as? CharSequence) ?:  entryValues?.indexOf(defaultNo as CharSequence) ?: 0
        spinner?.setSelection(selectedIndex)

        // Update summary asynchronously to prevent issues
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (selectedIndex >= 0) {
                summary = entries?.get(selectedIndex)
            }
        }, 0)

        // Handle selection changes
        spinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val newValue = entryValues?.get(position).toString()
                if (callChangeListener(newValue)) {
                    currentValue = newValue
                    persistString(newValue)
                    summary = entries?.get(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onClick() {
        // Open spinner dropdown when preference is clicked
        spinner?.performClick()
    }

    override fun onAttached() {
        super.onAttached()
        // Load persisted value
        currentValue = getPersistedString(defaultNo)
        persistString(getPersistedString(defaultNo))
    }
}
