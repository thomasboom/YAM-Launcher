package eu.ottop.yamlauncher.settings

import android.content.Context
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
class SpinnerPreference(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

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
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        spinner = holder.findViewById(R.id.preferenceOptions) as? Spinner
        val boundSpinner = spinner ?: return
        val labels = entries.orEmpty()
        val values = entryValues.orEmpty()
        val optionCount = minOf(labels.size, values.size)
        if (optionCount == 0) {
            boundSpinner.isEnabled = false
            return
        }

        // Set up adapter with entries
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            labels.take(optionCount),
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        boundSpinner.adapter = adapter

        // Set current selection
        val selectedIndex = values.indexOfFirst { it.toString() == currentValue }
            .takeIf { it in 0 until optionCount }
            ?: values.indexOfFirst { it.toString() == defaultNo }
                .takeIf { it in 0 until optionCount }
            ?: 0
        boundSpinner.setSelection(selectedIndex, false)

        // Handle selection changes
        boundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position !in 0 until optionCount) return
                val newValue = values[position].toString()
                if (newValue == currentValue) return
                if (callChangeListener(newValue)) {
                    currentValue = newValue
                    persistString(newValue)
                    summary = labels[position]
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
        val fallback = defaultNo.orEmpty()
        currentValue = runCatching { getPersistedString(fallback) }.getOrDefault(fallback)
        val values = entryValues.orEmpty()
        val selectedIndex = values.indexOfFirst { it.toString() == currentValue }
            .takeIf { it in entries.orEmpty().indices }
            ?: values.indexOfFirst { it.toString() == fallback }
                .takeIf { it in entries.orEmpty().indices }
        summary = selectedIndex?.let { entries?.get(it) }
    }
}
