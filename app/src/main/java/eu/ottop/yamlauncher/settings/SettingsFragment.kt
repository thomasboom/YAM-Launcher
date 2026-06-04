package eu.ottop.yamlauncher.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.UIUtils

/**
 * Main settings fragment displaying top-level categories.
 * Provides navigation to all settings subpages.
 */
class SettingsFragment : PreferenceFragmentCompat(), TitleProvider {

    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private lateinit var logger: Logger

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val uiUtils = UIUtils(requireContext())

        sharedPreferenceManager = SharedPreferenceManager(requireContext())
        logger = Logger.getInstance(requireContext())

        // Find preference references
        val homePref = findPreference<Preference>("defaultHome")
        val uiSettings = findPreference<Preference>("uiSettings")
        val homeSettings = findPreference<Preference>("homeSettings")
        val appMenuSettings = findPreference<Preference>("appMenuSettings")
        val hiddenPref = findPreference<Preference>("hiddenApps")
        val backupPref = findPreference<Preference>("backup")
        val restorePref = findPreference<Preference>("restore")
        val exportLogsPref = findPreference<Preference>("exportLogs")
        val clearLogsPref = findPreference<Preference>("clearLogs")
        val aboutPref = findPreference<Preference>("aboutPage")
        val restartPref = findPreference<Preference>("restartLauncher")
        val resetPref = findPreference<Preference>("resetAll")

        // Set up click listeners for each preference
        homePref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                // Open system home settings
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(),
                        getString(R.string.unable_to_launch_settings), Toast.LENGTH_SHORT).show()
                }
                true }

        uiSettings?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                uiUtils.switchFragment(requireActivity(), UISettingsFragment())
                true }

        homeSettings?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                uiUtils.switchFragment(requireActivity(), HomeSettingsFragment())
                true }

        appMenuSettings?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                uiUtils.switchFragment(requireActivity(), AppMenuSettingsFragment())
                true }

        hiddenPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                uiUtils.switchFragment(requireActivity(), HiddenAppsFragment())
                true }

        backupPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (requireActivity() as SettingsActivity).createBackup()
                true }

        restorePref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (requireActivity() as SettingsActivity).restoreBackup()
                true }

        aboutPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                uiUtils.switchFragment(requireActivity(), AboutFragment())
                true }

        restartPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (requireActivity() as SettingsActivity).restartApp()
                true }

        resetPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                sharedPreferenceManager.resetAllPreferences()
                true }

        exportLogsPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (requireActivity() as SettingsActivity).exportLogs()
                true }

        clearLogsPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                logger.clearLogs()
                Toast.makeText(requireContext(), getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
                true }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uiUtils = UIUtils(requireContext())
        uiUtils.setTextColors(view)
    }

    override fun getTitle(): String {
        return getString(R.string.settings_title)
    }

}
