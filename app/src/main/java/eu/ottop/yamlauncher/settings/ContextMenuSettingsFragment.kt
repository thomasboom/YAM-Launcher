package eu.ottop.yamlauncher.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import eu.ottop.yamlauncher.R

/**
 * Context menu settings fragment.
 * Contains preferences for which actions appear in the app long-press menu.
 */
class ContextMenuSettingsFragment : PreferenceFragmentCompat(), TitleProvider {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.context_menu_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uiUtils = eu.ottop.yamlauncher.utils.UIUtils(requireContext())
        uiUtils.setTextColors(view)
    }

    override fun getTitle(): String {
        return getString(R.string.context_menu_settings_title)
    }
}
