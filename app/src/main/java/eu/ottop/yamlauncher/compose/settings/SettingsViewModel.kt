package eu.ottop.yamlauncher.compose.settings

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import eu.ottop.yamlauncher.MainActivity
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.compose.AppEntry
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.utils.AppUtils
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.StringUtils
import eu.ottop.yamlauncher.utils.WeatherSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    val prefs = SharedPreferenceManager(context)
    val stringUtils = StringUtils()
    val weatherSystem = WeatherSystem(context)
    val launcherApps: LauncherApps =
        context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val appUtils = AppUtils(context, launcherApps)
    private val logger = Logger.getInstance(context)
    private val defaultPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _tick.value += 1
        }

    init {
        defaultPrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    suspend fun installedApps(includeHidden: Boolean = true): List<AppEntry> =
        withContext(Dispatchers.Default) {
            appUtils.getInstalledApps(includeHidden).map { (info, user, profile) ->
                AppEntry(info, user, profile)
            }.also { refreshNameCache(it) }
        }

    suspend fun hiddenApps(): List<AppEntry> =
        withContext(Dispatchers.Default) {
            appUtils.getHiddenApps().map { (info, user, profile) ->
                AppEntry(info, user, profile)
            }.also { refreshNameCache(it) }
        }

    fun displayName(entry: AppEntry): String =
        prefs.getAppName(
            entry.componentString, entry.profile,
            eu.ottop.yamlauncher.utils.AppNameResolver.resolveBaseLabel(context, entry.info),
        ).toString()

    /**
     * Cached display names so per-keystroke filtering doesn't re-read
     * SharedPreferences (every getter copies the whole map) for every app.
     */
    private var nameCache = emptyMap<String, String>()

    private fun refreshNameCache(entries: List<AppEntry>) {
        val names = HashMap<String, String>(entries.size * 2)
        for (e in entries) {
            names["${e.componentString}#${e.profile}"] = displayName(e)
        }
        nameCache = names
    }

    fun cachedName(entry: AppEntry): String =
        nameCache["${entry.componentString}#${entry.profile}"] ?: displayName(entry)

    // ================= backup / restore / logs / restart =================

    fun backupTo(uri: Uri) {
        try {
            val allEntries = defaultPrefs.all
            val backupData = JSONObject().apply {
                put("app_id", context.packageName)
                put("schema_version", 2)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
                val data = JSONObject()
                for ((key, value) in allEntries) {
                    if (key == "isRestored") continue
                    val entry = JSONObject()
                    when (value) {
                        is String -> entry.put("value", value).put("type", "String")
                        is Int -> entry.put("value", value).put("type", "Int")
                        is Boolean -> entry.put("value", value).put("type", "Boolean")
                        is Long -> entry.put("value", value).put("type", "Long")
                        is Float -> entry.put("value", value).put("type", "Float")
                        is Set<*> -> {
                            val values = value.filterIsInstance<String>()
                            if (values.size != value.size) continue
                            entry.put("value", JSONArray(values)).put("type", "StringSet")
                        }
                        else -> continue
                    }
                    data.put(key, entry)
                }
                put("data", data)
            }
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(backupData.toString(4).toByteArray(Charsets.UTF_8))
            }
            logger.i("Settings", "Settings backup created successfully")
            Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            logger.e("Settings", "Failed to create settings backup", e)
            Toast.makeText(context, context.getString(R.string.backup_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreFrom(uri: Uri) {
        val jsonData = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (jsonData == null) {
            Toast.makeText(context, context.getString(R.string.restore_error), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val backupData = JSONObject(jsonData)
            if (backupData.getString("app_id") != context.packageName) {
                throw IllegalArgumentException(context.getString(R.string.restore_wrong_app))
            }
            val schemaVersion = backupData.optInt("schema_version", 1)
            val data = backupData.getJSONObject("data")
            defaultPrefs.edit {
                clear()
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entry = data.getJSONObject(key)
                    when (entry.optString("type", "")) {
                        "String" -> putString(key, entry.getString("value"))
                        "Int" -> putInt(key, entry.getInt("value"))
                        "Boolean" -> putBoolean(key, entry.getBoolean("value"))
                        "Long" -> putLong(key, entry.getLong("value"))
                        "Float" -> putFloat(key, entry.getDouble("value").toFloat())
                        "StringSet" -> {
                            if (schemaVersion >= 2) {
                                val array = entry.getJSONArray("value")
                                val set = mutableSetOf<String>()
                                for (i in 0 until array.length()) set.add(array.getString(i))
                                putStringSet(key, set)
                            }
                        }
                    }
                }
                putBoolean("isRestored", true)
            }
            prefs.repairPreferences()
            logger.i("Settings", "Settings restored successfully")
            Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            logger.w("Settings", "Restore failed: ${e.message}")
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            logger.e("Settings", "Failed to restore settings", e)
            Toast.makeText(context, context.getString(R.string.restore_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun exportLogsTo(uri: Uri) {
        try {
            val logContent = logger.getLogContent()
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(logContent.toByteArray())
            }
            Toast.makeText(context, context.getString(R.string.logs_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.logs_export_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun hasLogs(): Boolean = logger.getLogFileSize() > 0L

    fun clearLogs() {
        logger.clearLogs()
        Toast.makeText(context, context.getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
    }

    fun resetAll() {
        prefs.clearAllPreferences()
    }

    fun restart() {
        val restartIntent = Intent(context, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            pendingIntent.send()
        } catch (e: Exception) {
            logger.e("Settings", "Failed to restart app", e)
        }
    }
}
