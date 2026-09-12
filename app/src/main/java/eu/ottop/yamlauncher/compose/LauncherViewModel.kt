package eu.ottop.yamlauncher.compose

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserHandle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.database.getStringOrNull
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.settings.ShortcutSetting
import eu.ottop.yamlauncher.tasks.NotificationEventBus
import eu.ottop.yamlauncher.tasks.NotificationListener
import eu.ottop.yamlauncher.utils.AppNameResolver
import eu.ottop.yamlauncher.utils.AppUtils
import eu.ottop.yamlauncher.utils.CurboxApiClient
import eu.ottop.yamlauncher.utils.GestureUtils
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.StringUtils
import eu.ottop.yamlauncher.utils.WeatherSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed, launchable app together with its profile handle. */
data class AppEntry(
    val info: LauncherActivityInfo,
    val user: UserHandle,
    val profile: Int,
) {
    val componentString: String get() = info.componentName.flattenToString()
    val packageName: String get() = info.componentName.packageName
}

/**
 * Precomputed, fully stable row data for the app list.
 * All display data is resolved once per app-list/preference change on a
 * background thread, so list rows compose without touching
 * SharedPreferences (every getter copies the whole map) or the package
 * manager, and Compose can skip rows whose inputs didn't change.
 */
@Immutable
data class AppRowModel(
    val key: String,
    val name: String,
    val cleaned: String,
    val pinned: Boolean,
    val isWork: Boolean,
)

/** Snapshot of every preference the launcher UI reads. Recomputed on any pref change. */
data class UiPrefs(
    val bgColor: Int,
    val textColor: Int,
    val textString: String,
    val font: String,
    val textStyle: String,
    val shadow: Boolean,
    val barVisible: Boolean,
    val drawerDarkening: Boolean,
    val homescreenDarkening: Boolean,
    val animSpeed: Long,
    val swipeThreshold: Int,
    val swipeVelocity: Int,
    val confirmLaunch: Boolean,
    val blockRotation: Boolean,
    val settingsLocked: Boolean,
    val clockEnabled: Boolean,
    val clockAlign: String,
    val clockSizeSp: Float,
    val dateEnabled: Boolean,
    val dateSizeSp: Float,
    val batteryEnabled: Boolean,
    val weatherEnabled: Boolean,
    val screenTimeEnabled: Boolean,
    val clockClick: Boolean,
    val dateClick: Boolean,
    val shortcutCount: Int,
    val shortcutAlign: String,
    val shortcutVAlign: String,
    val shortcutSizeSp: Float,
    val shortcutWeight: Float,
    val shortcutsLocked: Boolean,
    val dotsEnabled: Boolean,
    val showHiddenInPick: Boolean,
    val gesturesEnabled: Map<String, Boolean>,
    val doubleTapEnabled: Boolean,
    val doubleTapAction: String,
    val appAlign: String,
    val appSizeSp: Float,
    val appSizePreset: String,
    val regionSizeSp: Float,
    val pinEnabled: Boolean,
    val infoEnabled: Boolean,
    val uninstallEnabled: Boolean,
    val renameEnabled: Boolean,
    val hideEnabled: Boolean,
    val closeEnabled: Boolean,
    val searchEnabled: Boolean,
    val searchAlign: String,
    val searchSizeSp: Float,
    val fuzzy: Boolean,
    val appSpacingDp: Int,
    val strictClick: Boolean,
    val autoKeyboard: Boolean,
    val autoLaunch: Boolean,
    val contactsEnabled: Boolean,
    val webSearchEnabled: Boolean,
    val alphabetEnabled: Boolean,
    val alphabetPosition: String,
)

private data class SearchEntry(val entry: AppEntry, val cleaned: String, val cleanedLower: String)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    val prefs = SharedPreferenceManager(context)
    private val defaultPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val stringUtils = StringUtils()
    private val logger = Logger.getInstance(context)
    val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val appUtils = AppUtils(context, launcherApps)
    private val gestureUtils = GestureUtils(context)
    private val weatherSystem = WeatherSystem(context)
    private val curboxApiClient = CurboxApiClient(context) {
        viewModelScope.launch { updateScreenTime() }
    }

    // ---- preference snapshot ----
    private val _uiPrefs = MutableStateFlow(readUiPrefs())
    val uiPrefs: StateFlow<UiPrefs> = _uiPrefs.asStateFlow()

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _uiPrefs.value = readUiPrefs()
        }

    init {
        defaultPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewModelScope.launch {
            NotificationEventBus.notificationsChanged.collect { refreshNotifPackages() }
        }
    }

    override fun onCleared() {
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        curboxApiClient.disconnect()
    }

    // ---- app list ----
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private var appsLoaded = false

    // ---- drawer / search state ----
    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()
    private val _drawerTab = MutableStateFlow(0) // 0 = apps, 1 = contacts
    val drawerTab: StateFlow<Int> = _drawerTab.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class)
    private val debouncedQuery = _query.debounce(50).stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )

    /** Precomputed row models; rebuilt when apps or any preference change. */
    private val models: StateFlow<List<AppRowModel>> =
        combine(_apps, _uiPrefs) { apps, _ -> buildModels(apps) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps matching the current query, relevance-ordered like the legacy filter. */
    val filteredApps: StateFlow<List<AppRowModel>> =
        combine(models, debouncedQuery, _uiPrefs) { models, rawQuery, p ->
            filterModels(models, rawQuery, p)
        }.flowOn(Dispatchers.Default).stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyList()
        )

    val isSearchActive: StateFlow<Boolean> =
        debouncedQuery.map { stringUtils.cleanString(it).isNullOrEmpty().not() }
            .flowOn(Dispatchers.Default).stateIn(
                viewModelScope, SharingStarted.Eagerly, false
            )

    val contacts: StateFlow<List<Pair<String, Int>>> =
        combine(debouncedQuery, _drawerTab, _uiPrefs) { q, tab, _ ->
            if (tab == 1 && prefs.areContactsEnabled()) queryContacts(stringUtils.cleanString(q).orEmpty())
            else emptyList()
        }.flowOn(Dispatchers.IO).stateIn(
            viewModelScope, SharingStarted.Eagerly, emptyList()
        )

    // ---- date line extras ----
    private val _weatherText = MutableStateFlow("")
    val weatherText: StateFlow<String> = _weatherText.asStateFlow()
    private val _batteryText = MutableStateFlow("")
    val batteryText: StateFlow<String> = _batteryText.asStateFlow()
    private val _screenTimeText = MutableStateFlow("")
    val screenTimeText: StateFlow<String> = _screenTimeText.asStateFlow()

    // ---- overlays / dialogs ----
    private val _shortcutPickSlot = MutableStateFlow<Int?>(null)
    val shortcutPickSlot: StateFlow<Int?> = _shortcutPickSlot.asStateFlow()
    private val _sheetApp = MutableStateFlow<AppEntry?>(null)
    val sheetApp: StateFlow<AppEntry?> = _sheetApp.asStateFlow()
    private val _renameApp = MutableStateFlow<AppEntry?>(null)
    val renameApp: StateFlow<AppEntry?> = _renameApp.asStateFlow()
    private val _confirmLaunch = MutableStateFlow<AppEntry?>(null)
    val confirmLaunch: StateFlow<AppEntry?> = _confirmLaunch.asStateFlow()
    private val _accessibilityPrompt = MutableStateFlow(false)
    val accessibilityPrompt: StateFlow<Boolean> = _accessibilityPrompt.asStateFlow()
    private val _notifPackages = MutableStateFlow<Set<String>>(emptySet())
    val notifPackages: StateFlow<Set<String>> = _notifPackages.asStateFlow()
    private val _isDefaultLauncher = MutableStateFlow(true)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    /** Suppresses home-shortcut clicks after a fling opened the drawer. */
    var suppressShortcutClick = false
        private set

    /** When false, onResume must not auto-close the drawer (uninstall / contact view). */
    var returnAllowed = true

    private var lastAutoLaunchedQuery = ""

    // ================= prefs snapshot =================

    private fun readUiPrefs(): UiPrefs {
        val gestureDirs = listOf("clock", "date", "left", "right", "doubleTap", "weather")
        return UiPrefs(
            bgColor = prefs.getBgColor(),
            textColor = prefs.getTextColor(),
            textString = prefs.getTextString(),
            font = prefs.getTextFont(),
            textStyle = prefs.getTextStyle(),
            shadow = prefs.isTextShadowEnabled(),
            barVisible = prefs.isBarVisible(),
            drawerDarkening = prefs.isAppDrawerDarkeningEnabled(),
            homescreenDarkening = prefs.isHomescreenDarkeningEnabled(),
            animSpeed = prefs.getAnimationSpeed(),
            swipeThreshold = prefs.getSwipeThreshold(),
            swipeVelocity = prefs.getSwipeVelocity(),
            confirmLaunch = prefs.isConfirmationEnabled(),
            blockRotation = prefs.isAutoRotationBlocked(),
            settingsLocked = prefs.isSettingsLocked(),
            clockEnabled = prefs.isClockEnabled(),
            clockAlign = prefs.getClockAlignment(),
            clockSizeSp = clockSize(prefs.getClockSize()),
            dateEnabled = prefs.isDateEnabled(),
            dateSizeSp = dateSize(prefs.getDateSize()),
            batteryEnabled = prefs.isBatteryEnabled(),
            weatherEnabled = prefs.isWeatherEnabled(),
            screenTimeEnabled = prefs.isScreenTimeEnabled(),
            clockClick = prefs.isClockGestureEnabled(),
            dateClick = prefs.isDateGestureEnabled(),
            shortcutCount = prefs.getShortcutNumber(),
            shortcutAlign = prefs.getShortcutAlignment(),
            shortcutVAlign = prefs.getShortcutVAlignment(),
            shortcutSizeSp = shortcutSize(prefs.getShortcutSize()),
            shortcutWeight = prefs.getShortcutWeight(),
            shortcutsLocked = prefs.areShortcutsLocked(),
            dotsEnabled = prefs.isNotificationDotsEnabled(),
            showHiddenInPick = prefs.showHiddenShortcuts(),
            gesturesEnabled = gestureDirs.associateWith { prefs.isGestureEnabled(it) },
            doubleTapEnabled = prefs.isDoubleTapEnabled(),
            doubleTapAction = prefs.getDoubleTapAction(),
            appAlign = prefs.getAppAlignment(),
            appSizeSp = appSize(prefs.getAppSize()),
            appSizePreset = prefs.getAppSize(),
            regionSizeSp = regionSize(prefs.getAppSize()),
            pinEnabled = prefs.isPinEnabled(),
            infoEnabled = prefs.isInfoEnabled(),
            uninstallEnabled = prefs.isUninstallEnabled(),
            renameEnabled = prefs.isRenameEnabled(),
            hideEnabled = prefs.isHideEnabled(),
            closeEnabled = prefs.isCloseEnabled(),
            searchEnabled = prefs.isSearchEnabled(),
            searchAlign = prefs.getSearchAlignment(),
            searchSizeSp = searchSize(prefs.getSearchSize()),
            fuzzy = prefs.isFuzzySearchEnabled(),
            appSpacingDp = prefs.getAppSpacing(),
            strictClick = prefs.isAppStrictClickEnabled(),
            autoKeyboard = prefs.isAutoKeyboardEnabled(),
            autoLaunch = prefs.isAutoLaunchEnabled(),
            contactsEnabled = prefs.areContactsEnabled(),
            webSearchEnabled = prefs.isWebSearchEnabled(),
            alphabetEnabled = prefs.isAlphabetIndexEnabled(),
            alphabetPosition = prefs.getAlphabetIndexPosition(),
        )
    }

    // ================= app loading / refresh =================

    /** Initial load, called once from the activity. */
    fun initialLoad() {
        if (appsLoaded) return
        appsLoaded = true
        viewModelScope.launch(Dispatchers.Default) {
            setApps(loadEntries(includeHidden = false))
        }
    }

    private suspend fun loadEntries(includeHidden: Boolean): List<AppEntry> =
        withContext(Dispatchers.Default) {
            appUtils.getInstalledApps(includeHidden).map { (info, user, profile) ->
                AppEntry(info, user, profile)
            }
        }

    /** Assigns a new app list; models rebuild from it on a background thread. */
    private fun setApps(entries: List<AppEntry>) {
        _apps.value = entries
    }

    /** Periodic refresh; skipped while searching, mirroring the legacy behavior. */
    suspend fun refreshApps() {
        if (isSearchActive.value || !appsLoaded) return
        try {
            val updated = loadEntries(includeHidden = _shortcutPickSlot.value != null && prefs.showHiddenShortcuts())
            if (!entriesEqual(_apps.value, updated)) setApps(updated)
        } catch (e: Exception) {
            logger.w("LauncherViewModel", "Error in refreshApps: ${e.message}")
        }
    }

    private fun entriesEqual(a: List<AppEntry>, b: List<AppEntry>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i].info.componentName != b[i].info.componentName || a[i].user != b[i].user) return false
        }
        return true
    }

    // ================= search =================

    private fun displayName(entry: AppEntry): String =
        prefs.getAppName(
            entry.componentString, entry.profile,
            AppNameResolver.resolveBaseLabel(context, entry.info),
        ).toString()

    /**
     * Cached display data for one-shot readers (bottom sheet). Hot list paths
     * use [models] instead. Rebuilt together with the models, never on the
     * main thread.
     */
    private var nameCache = emptyMap<String, String>()
    private var cleanedCache = emptyMap<String, String>()
    private var pinnedCache = emptySet<String>()

    @Volatile
    private var entryByKey = emptyMap<String, AppEntry>()

    private fun cacheKey(entry: AppEntry) = "${entry.componentString}#${entry.profile}"

    internal fun keyOf(entry: AppEntry): String = cacheKey(entry)

    private fun buildModels(apps: List<AppEntry>): List<AppRowModel> {
        val names = HashMap<String, String>(apps.size * 2)
        val cleaned = HashMap<String, String>(apps.size * 2)
        val pinned = HashSet<String>()
        val byKey = HashMap<String, AppEntry>(apps.size * 2)
        val out = ArrayList<AppRowModel>(apps.size)
        for (e in apps) {
            val k = cacheKey(e)
            val name = try {
                displayName(e)
            } catch (ex: Exception) {
                logger.w("LauncherViewModel", "Error indexing app: ${e.packageName}")
                continue
            }
            val clean = stringUtils.cleanString(name).orEmpty()
            names[k] = name
            cleaned[k] = clean
            val isPinned = prefs.isAppPinned(e.componentString, e.profile)
            if (isPinned) pinned.add(k)
            byKey[k] = e
            out.add(AppRowModel(k, name, clean, isPinned, e.profile != 0))
        }
        nameCache = names
        cleanedCache = cleaned
        pinnedCache = pinned
        entryByKey = byKey
        return out
    }

    fun entryOf(key: String): AppEntry? = entryByKey[key]

    /** O(1) cached display name for hot paths; falls back to a direct read. */
    fun cachedName(entry: AppEntry): String =
        nameCache[cacheKey(entry)] ?: displayName(entry)

    /** O(1) cached cleaned (search-normalized) name for hot paths. */
    fun cachedCleaned(entry: AppEntry): String =
        cleanedCache[cacheKey(entry)]
            ?: stringUtils.cleanString(displayName(entry)).orEmpty()

    /** O(1) cached pin state for hot paths. */
    fun cachedPinned(entry: AppEntry): Boolean =
        pinnedCache.contains(cacheKey(entry))

    private fun alphabetKey(cleaned: String): String {
        val trimmed = cleaned.trimStart()
        if (trimmed.isEmpty()) return "#"
        val c = trimmed.first()
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }

    /** Letters available in the given list, for the alphabet index. */
    fun alphabetLetters(models: List<AppRowModel>): Set<String> =
        models.asSequence().map {
            alphabetKey(it.cleaned)
        }.filter { it.isNotEmpty() }.toSet()

    /** Index of the first entry starting with the letter, or -1. */
    fun firstPositionForLetter(models: List<AppRowModel>, letter: String): Int {
        val isNonLetter = letter == "#"
        return models.indexOfFirst {
            val key = alphabetKey(it.cleaned)
            if (isNonLetter) key == "#" else key == letter
        }
    }

    private fun filterModels(
        models: List<AppRowModel>,
        rawQuery: String,
        p: UiPrefs,
    ): List<AppRowModel> {
        val cleanQuery = stringUtils.cleanString(rawQuery) ?: return models
        if (cleanQuery.isEmpty()) return models
        val queryLower = cleanQuery.lowercase()
        val fuzzyPattern = if (p.fuzzy) stringUtils.getFuzzyPattern(cleanQuery) else null
        val exact = mutableListOf<AppRowModel>()
        val prefix = mutableListOf<AppRowModel>()
        val other = mutableListOf<AppRowModel>()
        for (model in models) {
            val cleaned = model.cleaned
            if (cleaned.isEmpty()) continue
            val cleanedLower = cleaned.lowercase()
            val fuzzyMatch = fuzzyPattern?.containsMatchIn(cleaned) == true
            if (!cleanedLower.contains(queryLower) && !fuzzyMatch) continue
            when {
                cleanedLower == queryLower -> exact.add(model)
                cleanedLower.startsWith(queryLower) -> prefix.add(model)
                else -> other.add(model)
            }
        }
        return exact + prefix + other
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    private fun queryContacts(filter: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                arrayOf("%$filter%"),
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                while (it.moveToNext()) {
                    val name = it.getStringOrNull(nameIndex)
                    val id = it.getStringOrNull(idIndex)?.toIntOrNull()
                    if (name != null && id != null) result.add(name to id)
                }
            }
        } catch (e: SecurityException) {
            logger.w("LauncherViewModel", "Permission denied for contacts: ${e.message}")
        } catch (e: Exception) {
            logger.e("LauncherViewModel", "Error getting contacts", e)
        }
        return result
    }

    // ================= drawer =================

    fun openDrawer() {
        suppressShortcutClick = false
        _drawerTab.value = 0
        _drawerOpen.value = true
    }

    /** Open without clearing shortcut-pick state (used when assigning a shortcut). */
    fun openDrawerForPick() {
        _drawerTab.value = 0
        _drawerOpen.value = true
    }

    fun openDrawerFromFling() {
        suppressShortcutClick = true
        _drawerTab.value = 0
        _drawerOpen.value = true
    }

    fun openDrawerWithChar(char: String) {
        _shortcutPickSlot.value = null
        _drawerTab.value = 0
        _query.value = char
        lastAutoLaunchedQuery = ""
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
        _drawerTab.value = 0
        _query.value = ""
        lastAutoLaunchedQuery = ""
        _shortcutPickSlot.value = null
        _sheetApp.value = null
        _renameApp.value = null
        _confirmLaunch.value = null
        suppressShortcutClick = false
    }

    fun switchTab() {
        _drawerTab.value = if (_drawerTab.value == 0) 1 else 0
    }

    /** Auto-launch when the filter narrows to a single app (once per query). */
    fun maybeAutoLaunch(filtered: List<AppRowModel>) {
        val p = _uiPrefs.value
        val q = _query.value
        if (!p.autoLaunch || _drawerTab.value != 0 || _shortcutPickSlot.value != null) return
        if (q.isEmpty() || filtered.size != 1 || q == lastAutoLaunchedQuery) return
        lastAutoLaunchedQuery = q
        entryOf(filtered[0].key)?.let { launch(it) }
    }

    /** Row tap: assign to the shortcut slot being picked, or launch. */
    fun onRowClick(model: AppRowModel) {
        val entry = entryOf(model.key) ?: return
        val slot = _shortcutPickSlot.value
        if (slot != null) assignAppShortcut(slot, model.name, entry)
        else launch(entry)
    }

    /** Row long-press: open the action sheet. */
    fun onRowLongClick(model: AppRowModel) {
        entryOf(model.key)?.let { openSheet(it) }
    }

    // ================= launching =================

    fun launch(entry: AppEntry) {
        if (_uiPrefs.value.confirmLaunch) _confirmLaunch.value = entry
        else appUtils.startApp(entry.info.componentName, entry.user)
    }

    fun confirmLaunchYes() {
        _confirmLaunch.value?.let { appUtils.startApp(it.info.componentName, it.user) }
        _confirmLaunch.value = null
    }

    fun dismissConfirm() {
        _confirmLaunch.value = null
    }

    fun launchGestureTarget(
        target: Pair<LauncherActivityInfo?, Int?>,
        gestureKey: String,
        requireEnabled: Boolean = true,
        fallback: () -> Unit,
    ) {
        val (activity, profileIndex) = target
        val enabled = !requireEnabled || _uiPrefs.value.gesturesEnabled[gestureKey] == true
        if (enabled && activity != null && profileIndex != null &&
            profileIndex in launcherApps.profiles.indices
        ) {
            appUtils.startApp(activity.componentName, launcherApps.profiles[profileIndex])
        } else {
            fallback()
        }
    }

    fun gestureTarget(direction: String): Pair<LauncherActivityInfo?, Int?> =
        gestureUtils.getSwipeInfo(launcherApps, direction)

    fun openAppInfo(entry: AppEntry, activityContext: Context) {
        try {
            launcherApps.startAppDetailsActivity(entry.info.componentName, entry.user, null, null)
        } catch (e: SecurityException) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", entry.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activityContext.startActivity(intent)
            } catch (e2: Exception) {
                logger.e("LauncherViewModel", "Failed to open app details via Intent fallback", e2)
                Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            logger.e("LauncherViewModel", "Failed to open app details", e)
            Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstallIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_DELETE).apply {
            data = "package:${entry.packageName}".let { Uri.parse(it) }
            putExtra(Intent.EXTRA_USER, entry.user)
        }

    fun isSystemApp(entry: AppEntry): Boolean =
        entry.info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

    fun onContactClick(contactId: Int, activityContext: Context) {
        returnAllowed = false
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
        activityContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun webSearch(query: String, activityContext: Context) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            activityContext.startActivity(intent)
        } else {
            logger.w("LauncherViewModel", "No browser app found for web search")
            Toast.makeText(context, "No browser app found.", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= shortcuts =================

    internal fun shortcutAt(index: Int): ShortcutSetting? = prefs.getShortcutSetting(index)

    fun beginShortcutPick(slot: Int) {
        _shortcutPickSlot.value = slot
        if (prefs.showHiddenShortcuts()) {
            viewModelScope.launch(Dispatchers.Default) {
                setApps(loadEntries(includeHidden = true))
                withContext(Dispatchers.Main) { openDrawerForPick() }
            }
        } else {
            openDrawerForPick()
        }
    }

    fun assignAppShortcut(slot: Int, label: CharSequence, entry: AppEntry) {
        prefs.setShortcut(slot, label, entry.componentString, entry.profile, false)
        logger.i("LauncherViewModel", "Shortcut $slot set to ${entry.componentString}")
        viewModelScope.launch(Dispatchers.Default) {
            setApps(loadEntries(includeHidden = false))
        }
        closeDrawer()
    }

    fun assignContactShortcut(slot: Int, contactId: Int, contactName: String) {
        prefs.setShortcut(slot, contactName, contactName, contactId, true)
        closeDrawer()
    }

    internal fun renameShortcut(slot: Int, newLabel: String, current: ShortcutSetting) {
        prefs.setShortcut(slot, newLabel, current.componentName, current.profile, current.isContact)
        closeDrawer()
    }

    /** Launch a home shortcut; contact shortcuts are handled by the caller. */
    internal fun launchShortcut(setting: ShortcutSetting): Boolean {
        if (setting.isContact) return false
        val profileIndex = setting.profile
        if (profileIndex !in launcherApps.profiles.indices) {
            logger.w("LauncherViewModel", "Failed to launch shortcut: invalid profile index")
            Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            return false
        }
        val userHandle = launcherApps.profiles[profileIndex]
        val componentString = setting.componentName
        if (componentString.isEmpty() || componentString == "e") {
            Toast.makeText(context, context.getString(R.string.shortcut_default_click), Toast.LENGTH_SHORT).show()
            return false
        }
        val component: ComponentName? = if (componentString.contains("/")) {
            val parts = componentString.split("/", limit = 2)
            if (parts.size != 2) null
            else {
                val cn = ComponentName(parts[0], parts[1])
                if (launcherApps.getActivityList(parts[0], userHandle).none { it.componentName == cn }) null else cn
            }
        } else {
            launcherApps.getActivityList(componentString, userHandle).firstOrNull()?.componentName
        }
        if (component == null) {
            logger.w("LauncherViewModel", "Failed to launch shortcut: $componentString not found")
            Toast.makeText(context, context.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
            return false
        }
        appUtils.startApp(component, userHandle)
        return true
    }

    // ================= app actions =================

    fun openSheet(entry: AppEntry) {
        _sheetApp.value = entry
    }

    fun closeSheet() {
        _sheetApp.value = null
    }

    fun togglePin(entry: AppEntry) {
        prefs.setPinnedApp(entry.componentString, entry.profile)
        val pinned = prefs.isAppPinned(entry.componentString, entry.profile)
        logger.i("LauncherViewModel", "App ${entry.info.label} ${if (pinned) "pinned" else "unpinned"}")
        closeSheet()
        viewModelScope.launch(Dispatchers.Default) {
            setApps(loadEntries(includeHidden = _shortcutPickSlot.value != null))
        }
    }

    fun hideApp(entry: AppEntry) {
        logger.i("LauncherViewModel", "Hiding app: ${entry.info.label}")
        prefs.setAppHidden(entry.componentString, entry.profile, true)
        closeSheet()
        viewModelScope.launch(Dispatchers.Default) {
            setApps(loadEntries(includeHidden = _shortcutPickSlot.value != null))
        }
    }

    fun beginRename(entry: AppEntry) {
        closeSheet()
        _renameApp.value = entry
    }

    fun cancelRename() {
        _renameApp.value = null
    }

    fun confirmRename(entry: AppEntry, newName: String): Boolean {
        if (newName.isBlank()) {
            Toast.makeText(context, context.getString(R.string.empty_rename), Toast.LENGTH_SHORT).show()
            return false
        }
        prefs.setAppName(entry.componentString, entry.profile, newName)
        logger.i("LauncherViewModel", "App renamed from '${entry.info.label}' to '$newName'")
        _renameApp.value = null
        return true
    }

    fun resetAppName(entry: AppEntry) {
        prefs.resetAppName(entry.componentString, entry.profile)
        _renameApp.value = null
    }

    fun renameDisplayName(entry: AppEntry): String = cachedName(entry)

    fun isAppPinned(entry: AppEntry): Boolean = cachedPinned(entry)

    // ================= weather / battery / screen time =================

    suspend fun updateWeather() {
        withContext(Dispatchers.IO) {
            if (prefs.isWeatherEnabled()) {
                if (prefs.isWeatherGPS()) weatherSystem.setGpsLocation { updateWeatherText() }
                else updateWeatherText()
            } else {
                _weatherText.value = ""
            }
        }
    }

    suspend fun updateWeatherText() {
        val temp = withContext(Dispatchers.IO) { weatherSystem.getTemp() }
        _weatherText.value = temp
    }

    fun setBatteryText(value: String) {
        _batteryText.value = value
    }

    fun clearBatteryText() {
        _batteryText.value = ""
    }

    suspend fun updateScreenTime() {
        if (!prefs.isScreenTimeEnabled()) {
            curboxApiClient.disconnect()
            _screenTimeText.value = ""
            return
        }
        if (!curboxApiClient.connect()) {
            _screenTimeText.value = ""
            return
        }
        val minutes = curboxApiClient.getTodayScreenTimeMinutes()
        if (minutes == null) {
            _screenTimeText.value = ""
            return
        }
        val duration = if (minutes >= 60) {
            context.getString(R.string.screen_time_hours_minutes, minutes / 60, minutes % 60)
        } else {
            context.getString(R.string.screen_time_minutes, minutes)
        }
        _screenTimeText.value = context.getString(R.string.screen_time_display, duration)
    }

    // ================= notifications / misc =================

    /** Binder IPC — always off the main thread. */
    fun refreshNotifPackages() {
        if (!prefs.isNotificationDotsEnabled()) {
            _notifPackages.value = emptySet()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            _notifPackages.value =
                NotificationListener.getInstance()?.getPackagesWithNotifications() ?: emptySet()
        }
    }

    fun notificationsAvailable(): Boolean = NotificationListener.getInstance() != null

    fun notificationsEnabled(ctx: Context): Boolean = NotificationListener.isEnabled(ctx)

    fun requestNotificationPermission(ctx: Context) = NotificationListener.requestPermission(ctx)

    fun checkDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        _isDefaultLauncher.value = resolve?.activityInfo?.packageName == context.packageName
    }

    fun promptAccessibility() {
        _accessibilityPrompt.value = true
    }

    fun dismissAccessibilityPrompt() {
        _accessibilityPrompt.value = false
    }

    fun accessibilityServiceEnabled(): Boolean =
        GestureUtils(context).isAccessibilityServiceEnabled(
            eu.ottop.yamlauncher.tasks.ScreenLockService::class.java
        )

    fun lockScreen() {
        val intent = Intent(context, eu.ottop.yamlauncher.tasks.ScreenLockService::class.java)
        intent.action = "LOCK_SCREEN"
        try {
            context.startService(intent)
        } catch (e: Exception) {
            logger.w("LauncherViewModel", "Failed to start ScreenLockService: ${e.message}")
        }
    }

    fun expandNotifications() {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (e: ReflectiveOperationException) {
            logger.w("LauncherViewModel", "Unable to expand the notification panel: ${e.message}")
        } catch (e: SecurityException) {
            logger.w("LauncherViewModel", "Notification panel access denied: ${e.message}")
        }
    }
}
