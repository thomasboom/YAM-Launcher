package eu.ottop.yamlauncher.settings

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import eu.ottop.yamlauncher.MainActivity
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.databinding.ActivitySettingsBinding
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.PermissionUtils
import eu.ottop.yamlauncher.utils.UIUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings activity hosting all settings fragments.
 * Handles backup/restore, permissions, and app restart.
 */
class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private companion object {
        private const val BACKUP_SCHEMA_VERSION = 2
        private const val BACKUP_FILE_BASENAME = "yamlauncher_backup"
        private const val TRANSIENT_PREF_KEY_RESTORED = "isRestored"
    }

    private val permissionUtils = PermissionUtils()

    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private lateinit var preferences: SharedPreferences
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var performBackup: ActivityResultLauncher<Intent>
    private lateinit var performRestore: ActivityResultLauncher<Intent>
    private lateinit var performLogExport: ActivityResultLauncher<Intent>
    private lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiUtils = UIUtils(this@SettingsActivity)

        logger = Logger.getInstance(this@SettingsActivity)
        sharedPreferenceManager = SharedPreferenceManager(this@SettingsActivity)
        preferences = PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity)
        logger.i("SettingsActivity", "SettingsActivity started")

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up UI
        uiUtils.setBackground(window, true)
        preferences.registerOnSharedPreferenceChangeListener(this)

        // Configure action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        uiUtils.adjustInsets(binding.root)

        // Load initial fragment if none exists
        if (supportFragmentManager.backStackEntryCount == 0) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsLayout, SettingsFragment())
                .commit()
        }

        // Update action bar title on back stack changes
        supportFragmentManager.addOnBackStackChangedListener {
            updateActionBarTitle()
        }

        // Register activity result launchers for file operations
        performBackup = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    saveSharedPreferencesToFile(uri)
                }
            }
        }

        performRestore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    restoreSharedPreferencesFromFile(uri)
                }
            }
        }

        performLogExport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportLogsToUri(uri)
                }
            }
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
        return true
    }

    /**
     * Updates action bar title based on current fragment.
     */
    private fun updateActionBarTitle() {
        val fragment = supportFragmentManager.findFragmentById(R.id.settingsLayout)
        if (fragment is TitleProvider) {
            supportActionBar?.title = fragment.getTitle()
        }
    }

    /**
     * Initiates backup process.
     * Opens file picker for user to select save location.
     */
    fun createBackup() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "${BACKUP_FILE_BASENAME}_$timestamp.json")
        }
        performBackup.launch(createFileIntent)
    }

    /**
     * Saves all preferences to JSON file.
     */
    private fun saveSharedPreferencesToFile(uri: Uri) {
        val allEntries = preferences.all

        // Build JSON structure with metadata and data
        val backupData = JSONObject().apply {
            put("app_id", application.packageName)
            put("schema_version", BACKUP_SCHEMA_VERSION)
            put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))

            val data = JSONObject()
            for ((key, value) in allEntries) {
                // Skip transient keys
                if (key == TRANSIENT_PREF_KEY_RESTORED) continue

                val entry = JSONObject().apply {
                    when (value) {
                        is String -> put("value", value).put("type", "String")
                        is Int -> put("value", value).put("type", "Int")
                        is Boolean -> put("value", value).put("type", "Boolean")
                        is Long -> put("value", value).put("type", "Long")
                        is Float -> put("value", value).put("type", "Float")
                        is Set<*> -> {
                            val values = value.filterIsInstance<String>()
                            // Skip mixed-type sets
                            if (values.size != value.size) return@apply
                            put("value", JSONArray(values)).put("type", "StringSet")
                        }
                        else -> return@apply
                    }
                }
                if (!entry.has("type")) continue
                data.put(key, entry)
            }
            put("data", data)
        }

        val sharedPreferencesText = backupData.toString(4)

        // Write to file
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(sharedPreferencesText.toByteArray(Charsets.UTF_8))
            }
            logger.i("SettingsActivity", "Settings backup created successfully")
            Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            logger.e("SettingsActivity", "Failed to create settings backup", e)
            Toast.makeText(this, getString(R.string.backup_fail), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initiates restore process.
     * Opens file picker for user to select backup file.
     */
    fun restoreBackup() {
        val openFileIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        performRestore.launch(openFileIntent)
    }

    /**
     * Restores preferences from JSON file.
     */
    private fun restoreSharedPreferencesFromFile(uri: Uri) {
        val jsonData = readJsonFile(uri)
        if (jsonData != null) {
            try {
                val backupData = JSONObject(jsonData)

                // Verify this backup is for this app
                if (backupData.getString("app_id") != application.packageName) {
                    throw IllegalArgumentException(getString(R.string.restore_wrong_app))
                }

                val schemaVersion = backupData.optInt("schema_version", 1)
                val data = backupData.getJSONObject("data")

                // Clear existing and restore
                val editor = preferences.edit().clear()

                val keys = data.keys()

                while (keys.hasNext()){
                    val key = keys.next()
                    val entry = data.getJSONObject(key)
                    val type = entry.optString("type", "")
                    if (type.isEmpty()) {
                        continue
                    }

                    // Restore based on type
                    when (type) {
                        "String" -> editor.putString(key, entry.getString("value"))
                        "Int" -> editor.putInt(key, entry.getInt("value"))
                        "Boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                        "Long" -> editor.putLong(key, entry.getLong("value"))
                        "Float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                        "StringSet" -> {
                            if (schemaVersion >= 2) {
                                val array = entry.getJSONArray("value")
                                val set = mutableSetOf<String>()
                                for (i in 0 until array.length()) {
                                    set.add(array.getString(i))
                                }
                                editor.putStringSet(key, set)
                            }
                        }
                        else -> {}
                    }
                }
                // Mark as restored for trigger
                editor.putBoolean(TRANSIENT_PREF_KEY_RESTORED, true)

                editor.apply()

                logger.i("SettingsActivity", "Settings restored successfully")
                Toast.makeText(this, getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
            } catch(e: IllegalArgumentException) {
                logger.w("SettingsActivity", "Restore failed: ${e.message}")
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                logger.e("SettingsActivity", "Failed to restore settings", e)
                Toast.makeText(this, getString(R.string.restore_fail), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.restore_error), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Reads JSON file content.
     */
    private fun readJsonFile(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
                reader?.readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Requests location permission for GPS weather.
     */
    fun requestLocationPermission() {
        try {
            ActivityCompat.requestPermissions(
                this@SettingsActivity,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                0
            )
        } catch(_: Exception) {}
    }

    /**
     * Requests contacts permission.
     */
    fun requestContactsPermission() {
        try {
            ActivityCompat.requestPermissions(
                this@SettingsActivity,
                arrayOf(Manifest.permission.READ_CONTACTS),
                1
            )
        } catch(_: Exception) {}
    }

    /**
     * Restarts the launcher app.
     * Used after reset to apply default settings.
     */
    fun restartApp() {
        val restartIntent = Intent(applicationContext, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent.send()
    }

    /**
     * Initiates log export.
     * Opens file picker for user to select save location.
     */
    fun exportLogs() {
        val logger = Logger.getInstance(this)
        if (logger.getLogFileSize() == 0L) {
            Toast.makeText(this, getString(R.string.no_logs_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val createFileIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "yam_launcher_logs_$timestamp.txt")
        }
        performLogExport.launch(createFileIntent)
    }

    /**
     * Writes log content to file.
     */
    private fun exportLogsToUri(uri: Uri) {
        try {
            val logger = Logger.getInstance(this)
            val logContent = logger.getLogContent()

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(logContent.toByteArray())
            }
            Toast.makeText(this, getString(R.string.logs_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.logs_export_fail), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Handle location permission result
        if (requestCode == 0) {
            try {
                val fragment = supportFragmentManager.findFragmentById(R.id.settingsLayout) as? HomeSettingsFragment
                if (fragment != null) {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        fragment.setLocationPreference(true)
                    } else {
                        Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
                        fragment.setLocationPreference(false)
                    }
                }
            } catch (e: Exception) {
                logger.e("SettingsActivity", "Error handling location permission result", e)
            }
        }

        // Handle contacts permission result
        if (requestCode == 1) {
            try {
                val fragment = supportFragmentManager.findFragmentById(R.id.settingsLayout) as? AppMenuSettingsFragment
                if (fragment != null) {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        fragment.setContactPreference(true)
                    } else {
                        Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
                        fragment.setContactPreference(false)
                    }
                }
            } catch (e: Exception) {
                logger.e("SettingsActivity", "Error handling contacts permission result", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Verify permissions are still granted
        if (!permissionUtils.hasPermission(this@SettingsActivity, Manifest.permission.READ_CONTACTS)) {
            sharedPreferenceManager.setContactsEnabled(false)
        }
        if (!permissionUtils.hasPermission(this@SettingsActivity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            sharedPreferenceManager.setWeatherGPS(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        // Update background color when changed
        if (key == "bgColor") {
            val uiUtils = UIUtils(this@SettingsActivity)
            uiUtils.setBackground(window, true)
        }
    }
}
