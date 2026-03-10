package eu.ottop.yamlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.database.Cursor
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewSwitcher
import androidx.appcompat.widget.AppCompatButton
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.database.getStringOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import eu.ottop.yamlauncher.databinding.ActivityMainBinding
import eu.ottop.yamlauncher.settings.SettingsActivity
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.tasks.BatteryReceiver
import eu.ottop.yamlauncher.tasks.NotificationListener
import eu.ottop.yamlauncher.tasks.ScreenLockService
import eu.ottop.yamlauncher.utils.Animations
import eu.ottop.yamlauncher.utils.AppMenuEdgeFactory
import eu.ottop.yamlauncher.utils.AppMenuLinearLayoutManager
import eu.ottop.yamlauncher.utils.AppNameResolver
import eu.ottop.yamlauncher.utils.AppUtils
import eu.ottop.yamlauncher.utils.BiometricUtils
import eu.ottop.yamlauncher.utils.GestureUtils
import eu.ottop.yamlauncher.utils.Logger
import eu.ottop.yamlauncher.utils.PermissionUtils
import eu.ottop.yamlauncher.utils.StringUtils
import eu.ottop.yamlauncher.utils.UIUtils
import eu.ottop.yamlauncher.utils.WeatherSystem
import eu.ottop.yamlauncher.views.AlphabetIndexView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import kotlin.math.abs


class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, AppMenuAdapter.OnItemClickListener,
    AppMenuAdapter.OnShortcutListener, AppMenuAdapter.OnItemLongClickListener, ContactsAdapter.OnContactClickListener,
    ContactsAdapter.OnContactShortcutListener, AppActionBottomSheet.AppActionListener {

    private lateinit var weatherSystem: WeatherSystem
    private lateinit var appUtils: AppUtils
    private lateinit var biometricUtils: BiometricUtils

    private val stringUtils = StringUtils()
    private val permissionUtils = PermissionUtils()
    private lateinit var uiUtils: UIUtils
    private lateinit var gestureUtils: GestureUtils

    private val appMenuLinearLayoutManager = AppMenuLinearLayoutManager(this@MainActivity)
    private val contactMenuLinearLayoutManager = AppMenuLinearLayoutManager(this@MainActivity)
    private val appMenuEdgeFactory = AppMenuEdgeFactory(this@MainActivity)

    private lateinit var sharedPreferenceManager: SharedPreferenceManager

    private lateinit var animations: Animations
    private lateinit var logger: Logger

    private lateinit var clock: TextClock
    private var clockMargin = 0
    private lateinit var dateText: TextClock
    private var dateElements = mutableListOf<String>()

    private lateinit var menuView: ViewSwitcher
    private lateinit var menuTitle: TextInputEditText
    private lateinit var appRecycler: RecyclerView
    private lateinit var contactRecycler: RecyclerView
    private lateinit var alphabetIndex: AlphabetIndexView
    private lateinit var searchSwitcher: ImageView
    private lateinit var internetSearch: ImageView
    private lateinit var searchView: TextInputEditText
    private var appAdapter: AppMenuAdapter? = null
    private var contactAdapter: ContactsAdapter? = null
    private var batteryReceiver: BatteryReceiver? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var launcherApps: LauncherApps
    private lateinit var installedApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>
    private var currentFilteredApps: List<Triple<LauncherActivityInfo, UserHandle, Int>> = listOf()

    private lateinit var preferences: SharedPreferences

    private var isBatteryReceiverRegistered = false
    private var isNotificationReceiverRegistered = false
    private var isSearchActive = false
    private var isInitialOpen = false
    private var canLaunchShortcut = true
    private var showHidden = false
    private var searchJob: Job? = null
    private var isResettingSearch = false

    private data class AppSearchEntry(
        val item: Triple<LauncherActivityInfo, UserHandle, Int>,
        val cleaned: String,
        val cleanedLower: String
    )

    private var appSearchIndex: List<AppSearchEntry> = emptyList()
    private var appSearchIndexDirty = true

    private fun buildAppSearchIndex(apps: List<Triple<LauncherActivityInfo, UserHandle, Int>>): List<AppSearchEntry> {
        return apps.map { appItem ->
            val name = sharedPreferenceManager.getAppName(
                appItem.first.componentName.flattenToString(),
                appItem.third,
                AppNameResolver.resolveBaseLabel(this, appItem.first)
            ).toString()

            val cleaned = stringUtils.cleanString(name).orEmpty()
            AppSearchEntry(appItem, cleaned, cleaned.lowercase())
        }
    }

    private var swipeThreshold = 100
    private var swipeVelocityThreshold = 100

    private lateinit var clockApp: Pair<LauncherActivityInfo?, Int?>
    private lateinit var dateApp: Pair<LauncherActivityInfo?, Int?>

    private lateinit var leftSwipeActivity: Pair<LauncherActivityInfo?, Int?>
    private lateinit var rightSwipeActivity: Pair<LauncherActivityInfo?, Int?>
    private lateinit var doubleTapApp: Pair<LauncherActivityInfo?, Int?>

    private lateinit var gestureDetector: GestureDetector
    private lateinit var shortcutGestureDetector: GestureDetector

    var returnAllowed = true

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        biometricUtils = BiometricUtils(this)
        setSupportActionBar(null)

        setMainVariables()

        uiUtils.adjustInsets(binding.root)

        setShortcuts()

        setPreferences()

        setHomeListeners()

        // Task to update the app menu every 5 seconds
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshAppMenu()
                    delay(5000)
                }
            }
        }

        // Task to update the weather periodically
        lifecycleScope.launch(Dispatchers.IO) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateWeather()
                    delay(sharedPreferenceManager.getWeatherUpdateIntervalMs())
                }
            }
        }

        setupApps()

        // Check if default launcher banner should be shown
        updateDefaultLauncherBanner()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            uiUtils.setLayoutListener(binding.root) // Manually resize app menu on search since adjustResize no longer works with sdk 35
        }
    }

    private fun setMainVariables() {
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps

        logger = Logger.getInstance(this@MainActivity)
        logger.i("MainActivity", "MainActivity started")

        weatherSystem = WeatherSystem(this@MainActivity)
        appUtils = AppUtils(this@MainActivity, launcherApps)
        uiUtils = UIUtils(this@MainActivity)
        gestureUtils = GestureUtils(this@MainActivity)
        sharedPreferenceManager = SharedPreferenceManager(this@MainActivity)
        animations = Animations(this@MainActivity)

        gestureDetector = GestureDetector(this, GestureListener())
        shortcutGestureDetector = GestureDetector(this, TextGestureListener())

        clock = binding.textClock

        clockMargin = clock.marginLeft

        dateText = binding.textDate

        dateElements = mutableListOf(dateText.format12Hour.toString(), dateText.format24Hour.toString(), "", "")

        menuTitle = binding.menuTitle

        searchView = binding.searchView

        menuView = binding.menuView

        alphabetIndex = binding.alphabetIndex

        searchSwitcher = binding.searchSwitcher
        internetSearch = binding.internetSearch

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun setShortcuts() {
        val shortcuts = arrayOf(
            R.id.app1,
            R.id.app2,
            R.id.app3,
            R.id.app4,
            R.id.app5,
            R.id.app6,
            R.id.app7,
            R.id.app8,
            R.id.app9,
            R.id.app10,
            R.id.app11,
            R.id.app12,
            R.id.app13,
            R.id.app14,
            R.id.app15
        )

        for (i in shortcuts.indices) {

            val textView = findViewById<TextView>(shortcuts[i])
            val shortcutNo = sharedPreferenceManager.getShortcutNumber()

            // Only show the chosen number of shortcuts (default 4). Hide the rest.
            if (i >= shortcutNo!!) {
                textView.visibility = View.GONE
            } else {
                textView.visibility = View.VISIBLE

                val savedView = sharedPreferenceManager.getShortcut(i)

                // Set the non-work profile drawable by default
                textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null), null, null, null)

                shortcutListeners(i, textView, savedView)

                if (savedView?.get(1) != "e") {
                    setShortcutSetup(textView, savedView)
                } else {
                    unsetShortcutSetup(textView)
                }
            }
        }
        uiUtils.setShortcutsAlignment(binding.homeView)
        uiUtils.setShortcutsVAlignment(binding.topSpace, binding.bottomSpace)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun shortcutListeners(index: Int, textView: TextView, savedView: List<String>?) {
        // Don't go to settings on long click, but keep other gestures functional
        textView.setOnTouchListener { _, event ->
            shortcutGestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        ViewCompat.addAccessibilityAction(textView, getString(R.string.accessibility_set_shortcut)) { _, _ ->
            launchShortcutSelection(index, textView, savedView)
        }

        ViewCompat.addAccessibilityAction(textView, getString(R.string.settings_title)) { _, _ ->
            trySettings()
            true
        }

        ViewCompat.addAccessibilityAction(textView, getString(R.string.open_app_menu)) { _, _ ->
            openAppMenu()
            true
        }

        textView.setOnLongClickListener {
            launchShortcutSelection(index, textView, savedView)
        }
    }

    private fun launchShortcutSelection(index: Int, textView: TextView, savedView: List<String>?): Boolean {

        if (!sharedPreferenceManager.areShortcutsLocked()) {
            uiUtils.setMenuTitleAlignment(menuTitle)
            uiUtils.setMenuTitleSize(menuTitle)
            menuTitle.hint = textView.text
            menuTitle.setText(textView.text)
            menuTitle.visibility = View.VISIBLE
            if (savedView != null) {
                setRenameShortcutListener(index, textView)
            }
            appAdapter?.shortcutIndex = index
            appAdapter?.shortcutTextView = textView
            contactAdapter?.shortcutIndex = index
            contactAdapter?.shortcutTextView = textView
            internetSearch.visibility = View.GONE

            if (sharedPreferenceManager.showHiddenShortcuts()) {
                lifecycleScope.launch(Dispatchers.Default) {
                    showHidden = true
                    refreshAppMenu()
                    runOnUiThread {
                        toAppMenu() // This is intentionally slow to happen
                    }
                }
                return true
            }
            toAppMenu()

            return true
        }

        return false
    }

    private fun setRenameShortcutListener(index: Int, textView: TextView) {
        menuTitle.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (menuTitle.text.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, getString(R.string.empty_rename), Toast.LENGTH_SHORT).show()
                    return@setOnEditorActionListener true
                }
                val imm =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(menuTitle.windowToken, 0)
                val savedView = sharedPreferenceManager.getShortcut(index)!!
                textView.text = menuTitle.text
                try {
                    sharedPreferenceManager.setShortcut(
                        index,
                        textView.text,
                        savedView[0],
                        savedView[1].toInt(),
                        savedView.getOrNull(3)?.toBoolean() ?: false
                    )
                } catch (_: NumberFormatException) {
                    sharedPreferenceManager.setShortcut(
                        index,
                        textView.text,
                        savedView[0],
                        0,
                        savedView.getOrNull(3)?.toBoolean() ?: false
                    )
                }
                backToHome()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun toAppMenu() {
        uiUtils.setContactsVisibility(searchSwitcher, binding.searchLayout, binding.searchReplacement)

        try {
            // The menu opens from the top
            appRecycler.scrollToPosition(0)
            menuView.displayedChild = 0
            if (searchSwitcher.isVisible) {
                contactRecycler.scrollToPosition(0)
                setAppViewDetails()
            }
        } catch (_: UninitializedPropertyAccessException) {
        }
        animations.showApps(binding.homeView, binding.appView)
        animations.backgroundIn(this@MainActivity)
        if (sharedPreferenceManager.isAutoKeyboardEnabled()) {
            isInitialOpen = true
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            searchView.requestFocus()
            imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun unsetShortcutSetup(textView: TextView) {
        textView.text = getString(R.string.shortcut_default)
        unsetShortcutListeners(textView)
    }

    private fun unsetShortcutListeners(textView: TextView) {
        textView.setOnClickListener {
            if (canLaunchShortcut) {
                Toast.makeText(this, getString(R.string.shortcut_default_click), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setShortcutSetup(textView: TextView, savedView: List<String>?) {
        // Set the work profile drawable for work profile apps
        textView.text = savedView?.get(2)
        if (savedView != null && (savedView.getOrNull(3)?.toBoolean() == true)) {
            setShortcutContactListeners(textView, savedView[1].toInt())
            return
        }
        if (savedView?.get(1) != "0") {
            textView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_work_app, null), null, null, null)
        }

        setShortcutListeners(textView, savedView)
    }

    private fun setShortcutListeners(textView: TextView, savedView: List<String>?) {
        textView.setOnClickListener {
            if (savedView != null && canLaunchShortcut) {
                val profileIndex = savedView.getOrNull(1)?.toIntOrNull()
                if (profileIndex == null || profileIndex !in launcherApps.profiles.indices) {
                    logger.w("MainActivity", "Failed to launch shortcut: invalid profile index")
                    Toast.makeText(this, this.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val userHandle = launcherApps.profiles[profileIndex]
                val componentString = savedView.getOrNull(0)
                if (componentString.isNullOrEmpty()) {
                    logger.w("MainActivity", "Failed to launch shortcut: empty component")
                    Toast.makeText(this, this.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val componentName = if (componentString.contains("/")) {
                    val parts = componentString.split("/", limit = 2)
                    if (parts.size != 2) {
                        logger.w("MainActivity", "Failed to launch shortcut: invalid component $componentString")
                        Toast.makeText(this, this.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val packageName = parts[0]
                    val className = parts[1]
                    val cn = ComponentName(packageName, className)
                    if (launcherApps.getActivityList(packageName, userHandle).none { it.componentName == cn }) {
                        logger.w("MainActivity", "Failed to launch shortcut: $componentString not found")
                        Toast.makeText(this, this.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    cn
                } else {
                    val mainActivity = launcherApps.getActivityList(componentString, userHandle).firstOrNull()
                    if (mainActivity != null) {
                        mainActivity.componentName
                    } else {
                        logger.w("MainActivity", "Failed to launch shortcut: $componentString not found")
                        Toast.makeText(this, this.getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                appUtils.launchApp(componentName, userHandle)
            }
        }
    }

    private fun setShortcutContactListeners(textView: TextView, contactId: Int) {
        textView.setOnClickListener {
            onContactClick(contactId)
        }
    }

    private fun setPreferences() {
        uiUtils.setBackground(window)

        uiUtils.setTextFont(binding.homeView)
        uiUtils.setFont(searchView)
        uiUtils.setFont(menuTitle)

        uiUtils.setTextColors(binding.homeView)
        uiUtils.setStatusBarColor(window)

        uiUtils.setClockVisibility(clock)
        uiUtils.setDateVisibility(dateText)
        uiUtils.setSearchVisibility(searchView, binding.searchLayout, binding.searchReplacement)

        uiUtils.setClockAlignment(clock, dateText)
        uiUtils.setSearchAlignment(searchView)

        uiUtils.setClockSize(clock)
        uiUtils.setDateSize(dateText)
        uiUtils.setShortcutsSize(binding.homeView)
        uiUtils.setSearchSize(searchView)

        uiUtils.setShortcutsSpacing(binding.homeView)

        // This didn't work and somehow delaying it by 0 makes it work
        handler.postDelayed({
            uiUtils.setStatusBar(window)
            uiUtils.setMenuItemColors(searchView)
            uiUtils.setMenuItemColors(menuTitle, "A9")
        }, 100)

        clockApp = gestureUtils.getSwipeInfo(launcherApps, "clock")
        dateApp = gestureUtils.getSwipeInfo(launcherApps, "date")

        leftSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "left")
        rightSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "right")
        doubleTapApp = gestureUtils.getSwipeInfo(launcherApps, "doubleTap")

        swipeThreshold = sharedPreferenceManager.getSwipeThreshold()
        swipeVelocityThreshold = sharedPreferenceManager.getSwipeVelocity()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setHomeListeners() {
        registerBatteryReceiver()
        registerNotificationReceiver()

        if (!sharedPreferenceManager.isBatteryEnabled()) {
            unregisterBatteryReceiver()
        }

        preferences.registerOnSharedPreferenceChangeListener(this)

        binding.homeView.setOnTouchListener { _, event ->
            super.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        // Set up default launcher banner button
        findViewById<android.widget.TextView>(R.id.setDefaultLauncherButton)?.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                logger.w("MainActivity", "Unable to launch home settings")
                Toast.makeText(this, getString(R.string.unable_to_launch_settings), Toast.LENGTH_SHORT).show()
            }
        }

        clock.setOnClickListener { _ ->
            if (sharedPreferenceManager.isClockGestureEnabled()) {
                if (sharedPreferenceManager.isGestureEnabled("clock") && clockApp.first != null && clockApp.second != null) {
                    try {
                        launcherApps.startMainActivity(clockApp.first!!.componentName, launcherApps.profiles[clockApp.second!!], null, null)
                    } catch (e: Exception) {
                        logger.e("MainActivity", "Failed to launch clock gesture app", e)
                        Toast.makeText(this@MainActivity, getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }
            }
        }

        dateText.setOnClickListener { _ ->
            if (sharedPreferenceManager.isDateGestureEnabled()) {

                if (sharedPreferenceManager.isGestureEnabled("date") && dateApp.first != null && dateApp.second != null) {
                    try {
                        launcherApps.startMainActivity(dateApp.first!!.componentName, launcherApps.profiles[dateApp.second!!], null, null)
                    } catch (e: Exception) {
                        logger.e("MainActivity", "Failed to launch date gesture app", e)
                        Toast.makeText(this@MainActivity, getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        startActivity(
                            Intent(
                                Intent.makeMainSelectorActivity(
                                    Intent.ACTION_MAIN,
                                    Intent.CATEGORY_APP_CALENDAR
                                )
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        logger.w("MainActivity", "No calendar app found when clicking date")
                        Toast.makeText(this, getString(R.string.no_calendar_app), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        clock.setOnLongClickListener { _ ->
            trySettings()
            true
        }

        dateText.setOnLongClickListener { _ ->
            trySettings()
            true
        }

        ViewCompat.addAccessibilityAction(binding.homeView, getString(R.string.settings_title)) { _, _ ->
            trySettings()
            true
        }

        ViewCompat.addAccessibilityAction(binding.homeView, getString(R.string.open_app_menu)) { _, _ ->
            openAppMenu()
            true
        }

        // Return to home on back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backToHome()
            }
        })
    }

    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (sharedPreferenceManager.isSettingsLocked()) {
                biometricUtils.startBiometricSettingsAuth(object : BiometricUtils.CallbackSettings {
                    override fun onAuthenticationSucceeded() {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }

                    override fun onAuthenticationFailed() {
                        logger.w("MainActivity", "Biometric authentication failed")
                    }

                    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> logger.i("MainActivity", "Biometric authentication cancelled by user")
                            else -> logger.e("MainActivity", "Biometric authentication error: $errorMessage (code: $errorCode)")
                        }
                    }
                })
            } else {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            batteryReceiver = BatteryReceiver.register(this, this@MainActivity)
            isBatteryReceiverRegistered = true
        }
    }

    private fun unregisterBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            isBatteryReceiverRegistered = false
        }
    }

    private fun openAppMenu() {
        appAdapter?.shortcutTextView = null
        contactAdapter?.shortcutTextView = null
        menuTitle.visibility = View.GONE
        uiUtils.setWebSearchVisibility(internetSearch)
        toAppMenu()
    }

    // Only reload items that have had preferences changed
    @SuppressLint("UseKtx")
    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if (preferences != null) {
            when (key) {
                "bgColor" -> {
                    uiUtils.setBackground(window)
                }

                "textColor" -> {
                    uiUtils.setTextColors(binding.homeView)
                    uiUtils.setStatusBarColor(window)
                    uiUtils.setMenuItemColors(searchView)
                    uiUtils.setMenuItemColors(menuTitle, "A9")
                    uiUtils.setImageColor(searchSwitcher)
                    uiUtils.setImageColor(internetSearch)
                    if (sharedPreferenceManager.isAlphabetIndexEnabled()) {
                        alphabetIndex.setTextColor(sharedPreferenceManager.getTextColor())
                    }
                }

                "textFont" -> {
                    uiUtils.setTextFont(binding.homeView)
                    uiUtils.setFont(searchView)
                    uiUtils.setFont(menuTitle)
                }

                "textStyle" -> {
                    uiUtils.setTextFont(binding.homeView)
                    uiUtils.setFont(searchView)
                    uiUtils.setFont(menuTitle)
                }

                "textShadow" -> {
                    uiUtils.setTextColors(binding.homeView)
                    uiUtils.setMenuItemColors(searchView)
                    uiUtils.setMenuItemColors(menuTitle, "A9")
                    uiUtils.setImageColor(searchSwitcher)
                    uiUtils.setImageColor(internetSearch)
                }

                "clockEnabled" -> {
                    uiUtils.setClockVisibility(clock)
                }

                "dateEnabled" -> {
                    uiUtils.setDateVisibility(dateText)
                }

                "searchEnabled" -> {
                    uiUtils.setSearchVisibility(searchView, binding.searchLayout, binding.searchReplacement)
                }

                "contactsEnabled" -> {
                    try {
                        contactRecycler
                    } catch (_: UninitializedPropertyAccessException) {
                        setupContactRecycler()
                    }
                }

                "clockAlignment" -> {
                    uiUtils.setClockAlignment(clock, dateText)
                }

                "shortcutAlignment" -> {
                    uiUtils.setShortcutsAlignment(binding.homeView)
                }

                "shortcutVAlignment" -> {
                    uiUtils.setShortcutsVAlignment(binding.topSpace, binding.bottomSpace)
                }

                "searchAlignment" -> {
                    uiUtils.setSearchAlignment(searchView)
                }

                "clockSize" -> {
                    uiUtils.setClockSize(clock)
                }

                "dateSize" -> {
                    uiUtils.setDateSize(dateText)
                }

                "shortcutSize" -> {
                    uiUtils.setShortcutsSize(binding.homeView)
                }

                "searchSize" -> {
                    uiUtils.setSearchSize(searchView)
                }

                "shortcutWeight" -> {
                    uiUtils.setShortcutsSpacing(binding.homeView)
                }

                "barVisibility" -> {
                    uiUtils.setStatusBar(window)
                }

                "clockSwipe" -> {
                    clockApp = gestureUtils.getSwipeInfo(launcherApps, "clock")
                }

                "dateSwipe" -> {
                    dateApp = gestureUtils.getSwipeInfo(launcherApps, "date")
                }

                "clockSwipeApp" -> {
                    clockApp = gestureUtils.getSwipeInfo(launcherApps, "clock")
                }

                "dateSwipeApp" -> {
                    dateApp = gestureUtils.getSwipeInfo(launcherApps, "date")
                }

                "leftSwipe" -> {
                    leftSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "left")
                }

                "rightSwipe" -> {
                    rightSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "right")
                }

                "leftSwipeApp" -> {
                    leftSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "left")
                }

                "rightSwipeApp" -> {
                    rightSwipeActivity = gestureUtils.getSwipeInfo(launcherApps, "right")
                }

                "doubleTapAction" -> {
                    doubleTapApp = gestureUtils.getSwipeInfo(launcherApps, "doubleTap")
                }

                "doubleTapSwipeApp" -> {
                    doubleTapApp = gestureUtils.getSwipeInfo(launcherApps, "doubleTap")
                }

                "batteryEnabled" -> {
                    if (sharedPreferenceManager.isBatteryEnabled()) {
                        registerBatteryReceiver()
                    } else {
                        unregisterBatteryReceiver()
                        modifyDate("", 3)
                    }
                }

                "shortcutNo" -> {
                    setShortcuts()
                }

                "swipeThreshold" -> {
                    swipeThreshold = sharedPreferenceManager.getSwipeThreshold()
                }

                "swipeVelocity" -> {
                    swipeVelocityThreshold = sharedPreferenceManager.getSwipeVelocity()
                }

                "isRestored" -> {
                    preferences.edit { remove("isRestored") }
                    setPreferences()
                    setShortcuts()
                }

                "lockShortcuts" -> {
                    setShortcuts()
                }

                "alphabetIndexEnabled" -> {
                    setAlphabetIndexPosition()
                    if (sharedPreferenceManager.isAlphabetIndexEnabled()) {
                        setupAlphabetIndex()
                    } else {
                        alphabetIndex.visibility = View.GONE
                    }
                }

                "alphabetIndexPosition" -> {
                    setAlphabetIndexPosition()
                }

                "notificationDots" -> {
                    if (sharedPreferenceManager.isNotificationDotsEnabled()) {
                        if (!NotificationListener.isEnabled(this@MainActivity)) {
                            NotificationListener.requestPermission(this@MainActivity)
                        } else {
                            updateNotificationDots()
                        }
                    } else {
                        setShortcuts()
                    }
                }
            }
        }
    }

    fun modifyDate(value: String, index: Int) {
        /*Indexes:
        0 = 12h time
        1 = 24h time
        2 = Weather
        3 = Battery level*/
        dateElements[index] = value
        dateText.format12Hour = "${dateElements[0]}${stringUtils.addStartTextIfNotEmpty(dateElements[2], " | ")}${
            stringUtils.addStartTextIfNotEmpty(
                dateElements[3],
                " | "
            )
        }"
        dateText.format24Hour = "${dateElements[1]}${stringUtils.addStartTextIfNotEmpty(dateElements[2], " | ")}${
            stringUtils.addStartTextIfNotEmpty(
                dateElements[3],
                " | "
            )
        }"
    }

    fun backToHome(animSpeed: Long = sharedPreferenceManager.getAnimationSpeed()) {
        canLaunchShortcut = true
        showHidden = false

        // Clear search immediately to prevent race conditions
        searchJob?.cancel()
        isResettingSearch = true
        searchView.setText(R.string.empty)
        isResettingSearch = false  // Unblock immediately after programmatic setText
        isSearchActive = false

        closeKeyboard()
        animations.showHome(binding.homeView, binding.appView, animSpeed)
        animations.backgroundOut(this@MainActivity, animSpeed)

        // Single delayed operation - no 50ms gaps
        handler.postDelayed({
            try {
                appMenuLinearLayoutManager.setScrollEnabled(true)
                currentFilteredApps = installedApps
                // Explicitly reset adapter to show full list
                lifecycleScope.launch {
                    updateMenu(installedApps)
                    refreshAppMenu()
                }
            } catch (_: UninitializedPropertyAccessException) {
            }
        }, animSpeed)
    }

    private fun closeKeyboard() {
        val imm =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    suspend fun refreshAppMenu() {
        try {

            // Don't reset app menu while under a search
            if (!isSearchActive) {
                val updatedApps = appUtils.getInstalledApps(showHidden)
                if (!listsEqual(installedApps, updatedApps)) {

                    updateMenu(updatedApps)

                    installedApps = updatedApps
                    currentFilteredApps = updatedApps
                    appSearchIndexDirty = true

                    if (sharedPreferenceManager.isAlphabetIndexEnabled()) {
                        refreshAlphabetIndex(currentFilteredApps)
                    }
                }
            }
        } catch (_: UninitializedPropertyAccessException) {
        }
    }

    private fun listsEqual(
        list1: List<Triple<LauncherActivityInfo, UserHandle, Int>>,
        list2: List<Triple<LauncherActivityInfo, UserHandle, Int>>
    ): Boolean {
        if (list1.size != list2.size) return false

        for (i in list1.indices) {
            if (list1[i].first.componentName != list2[i].first.componentName || list1[i].second != list2[i].second) {
                return false
            }
        }

        return true
    }

    private suspend fun updateMenu(updatedApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        withContext(Dispatchers.Main) {
            if (isSearchActive) {
                appAdapter?.setApps(updatedApps)
            } else {
                appAdapter?.updateApps(updatedApps)
            }
        }
    }

    private suspend fun updateWeather() {
        withContext(Dispatchers.IO) {
            if (sharedPreferenceManager.isWeatherEnabled()) {
                if (sharedPreferenceManager.isWeatherGPS()) {
                    weatherSystem.setGpsLocation(this@MainActivity)
                } else {
                    updateWeatherText()
                }
            } else {
                withContext(Dispatchers.Main) {
                    modifyDate("", 2)
                }
            }
        }
    }

    suspend fun updateWeatherText() {
        val temp = weatherSystem.getTemp()
        withContext(Dispatchers.Main) {
            modifyDate(temp, 2)
        }
    }

    private fun setupApps() {
        lifecycleScope.launch(Dispatchers.Default) {
            installedApps = appUtils.getInstalledApps()
            currentFilteredApps = installedApps
            val newApps = installedApps.toMutableList()

            // Pre-build search index so the first keystroke doesn't hitch.
            appSearchIndex = buildAppSearchIndex(installedApps)
            appSearchIndexDirty = false

            setupAppRecycler(newApps)

            setupSearch()
            if (sharedPreferenceManager.areContactsEnabled()) {
                setupContactRecycler()
            }

            setupInternetSearch()

            setupAlphabetIndex()
        }
    }

    private fun setupAlphabetIndex() {
        if (!sharedPreferenceManager.isAlphabetIndexEnabled()) {
            return
        }
        lifecycleScope.launch(Dispatchers.Main) {
            alphabetIndex.setOnLetterSelectedListener { letter ->
                scrollToLetter(letter, currentFilteredApps)
            }
            refreshAlphabetIndex(currentFilteredApps)
        }
    }

    private fun refreshAlphabetIndex(apps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        val availableLetters = buildAlphabetIndexLetters(apps)
        alphabetIndex.post {
            if (!sharedPreferenceManager.isAlphabetIndexEnabled() || menuView.displayedChild != 0) {
                alphabetIndex.visibility = View.GONE
                return@post
            }
            alphabetIndex.setTextColor(sharedPreferenceManager.getTextColor())
            alphabetIndex.setAvailableLetters(availableLetters)
            setAlphabetIndexPosition()
            alphabetIndex.visibility = if (availableLetters.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun buildAlphabetIndexLetters(apps: List<Triple<LauncherActivityInfo, UserHandle, Int>>): Set<String> {
        val availableLetters = mutableSetOf<String>()
        for (app in apps) {
            val name = sharedPreferenceManager.getAppName(
                app.first.componentName.flattenToString(),
                app.third,
                AppNameResolver.resolveBaseLabel(this, app.first)
            ).toString()
            availableLetters.add(getAlphabetIndexKey(name))
        }
        return availableLetters
    }

    private fun getAlphabetIndexKey(name: String): String {
        val trimmed = name.trimStart()
        if (trimmed.isEmpty()) return "#"
        val firstChar = trimmed.first()
        return if (firstChar.isLetter()) firstChar.uppercaseChar().toString() else "#"
    }

    private fun setAlphabetIndexPosition() {
        val position = sharedPreferenceManager.getAlphabetIndexPosition()
        val layoutParams = alphabetIndex.layoutParams as android.widget.FrameLayout.LayoutParams

        when (position) {
            "left" -> {
                layoutParams.gravity = android.view.Gravity.START
            }
            "right" -> {
                layoutParams.gravity = android.view.Gravity.END
            }
        }
        alphabetIndex.layoutParams = layoutParams
    }

    private fun scrollToLetter(letter: String, apps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        val targetLetter = if (letter == "#") null else letter

        var targetPosition = -1
        for (i in apps.indices) {
            val app = apps[i]
            val name = sharedPreferenceManager.getAppName(
                app.first.componentName.flattenToString(),
                app.third,
                AppNameResolver.resolveBaseLabel(this, app.first)
            ).toString()

            val letterKey = getAlphabetIndexKey(name)
            if (targetLetter == null) {
                if (letterKey == "#") {
                    targetPosition = i
                    break
                }
            } else if (letterKey == targetLetter) {
                targetPosition = i
                break
            }
        }

        if (targetPosition >= 0) {
            appRecycler.scrollToPosition(targetPosition)
        }
    }

    private fun setupInternetSearch() {
        uiUtils.setImageColor(internetSearch)
        internetSearch.setOnClickListener {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchView.text.toString())
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                logger.w("MainActivity", "No browser app found for web search")
                Toast.makeText(this@MainActivity, "No browser app found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun setupAppRecycler(newApps: MutableList<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        appAdapter = AppMenuAdapter(this@MainActivity, binding, newApps, this@MainActivity, this@MainActivity, this@MainActivity, launcherApps)
        appMenuLinearLayoutManager.stackFromEnd = true
        appRecycler = binding.appRecycler
        withContext(Dispatchers.Main) {
            appRecycler.layoutManager = appMenuLinearLayoutManager
            appRecycler.edgeEffectFactory = appMenuEdgeFactory
            appRecycler.adapter = appAdapter

        }

        setupRecyclerListener(appRecycler, appMenuLinearLayoutManager)
    }

    // Inform the layout manager of scroll states to calculate whether the menu is on the top
    private fun setupRecyclerListener(recycler: RecyclerView, layoutManager: AppMenuLinearLayoutManager) {
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    layoutManager.setScrollInfo()
                }
            }
        })
    }

    private fun setupContactRecycler() {
        uiUtils.setImageColor(searchSwitcher)

        contactAdapter = ContactsAdapter(this, mutableListOf(), this@MainActivity, this@MainActivity)
        contactMenuLinearLayoutManager.stackFromEnd = true
        contactRecycler = binding.contactRecycler
        contactRecycler.layoutManager = contactMenuLinearLayoutManager
        contactRecycler.edgeEffectFactory = appMenuEdgeFactory
        contactRecycler.adapter = contactAdapter
        setupRecyclerListener(contactRecycler, contactMenuLinearLayoutManager)

        searchSwitcher.setOnClickListener {
            switchMenus()
        }
    }

    fun switchMenus() {
        menuView.showNext()
        when (menuView.displayedChild) {
            0 -> {
                setAppViewDetails()
            }

            1 -> {
                setContactViewDetails()
            }
        }
    }

    private fun setAppViewDetails() {
        lifecycleScope.launch {
            // Cancel any ongoing search job when switching views
            searchJob?.cancel()
            withContext(Dispatchers.Default) {
                filterItems(searchView.text.toString())
            }
        }
        searchSwitcher.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.contacts_24px,
                null
            )
        )
        searchSwitcher.contentDescription = getString(R.string.switch_to_contacts)
        refreshAlphabetIndex(currentFilteredApps)
    }

    private fun setContactViewDetails() {
        lifecycleScope.launch {
            // Cancel any ongoing search job when switching views
            searchJob?.cancel()
            withContext(Dispatchers.Default) {
                filterItems(searchView.text.toString())
            }
        }
        searchSwitcher.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.apps_24px, null))
        searchSwitcher.contentDescription = getString(R.string.switch_to_apps)
        alphabetIndex.visibility = View.GONE
    }

    private fun getContacts(filterString: String): MutableList<Pair<String, Int>> {
        val contacts = mutableListOf<Pair<String, Int>>()

        val contentResolver: ContentResolver = contentResolver

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$filterString%")

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            while (it.moveToNext()) {
                val name = it.getStringOrNull(nameIndex)
                val id = it.getStringOrNull(idIndex)?.toInt()
                if (name != null && id != null) {
                    contacts.add(Pair(name, id))
                }
            }
        }
        return contacts
    }

    private suspend fun updateContacts(filterString: String) {
        isSearchActive = filterString.isNotEmpty()
        val contacts = getContacts(filterString)
        withContext(Dispatchers.Main) {
            contactAdapter?.updateContacts(contacts)
        }
    }

    private fun setupSearch() {
        binding.appView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->

            if (bottom - top > oldBottom - oldTop) {
                // If keyboard is closed, remove cursor from the search bar
                searchView.clearFocus()
                menuTitle.clearFocus()
            } else if (bottom - top < oldBottom - oldTop && isInitialOpen) {
                isInitialOpen = false
                appRecycler.scrollToPosition(0)
            }

        }

        searchView.addTextChangedListener(object :
            TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel previous search job to implement debouncing
                searchJob?.cancel()
            }

            override fun afterTextChanged(s: Editable?) {
                searchJob = lifecycleScope.launch {
                    delay(50) // 50ms debounce delay - prevents excessive filtering during fast typing
                    while (isResettingSearch) {
                        delay(16) // Wait for reset to complete
                    }
                    withContext(Dispatchers.Default) {
                        filterItems(s.toString())
                    }
                }
            }
        })
    }

    private data class FilterResult(
        val filteredApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>?,
        val isEmptyQuery: Boolean
    )

    private suspend fun filterItems(query: String?) {
        val cleanQuery = stringUtils.cleanString(query)
        when (menuView.displayedChild) {
            0 -> {
                val appsToFilter = installedApps
                val result = withContext(Dispatchers.Default) {
                    getFilteredApps(cleanQuery, appsToFilter)
                }
                withContext(Dispatchers.Main) {
                    if (result.isEmptyQuery) {
                        isSearchActive = false
                        currentFilteredApps = installedApps
                        updateMenu(installedApps)
                        refreshAlphabetIndex(currentFilteredApps)
                    } else if (result.filteredApps != null) {
                        isSearchActive = true
                        applySearchFilter(result.filteredApps)
                    }
                }
            }

            1 -> {
                if (sharedPreferenceManager.areContactsEnabled() && cleanQuery != null) {
                    updateContacts(cleanQuery)
                }
            }
        }
    }

    private suspend fun getFilteredApps(
        cleanQuery: String?,
        updatedApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>
    ): FilterResult {
        if (cleanQuery.isNullOrEmpty()) {
            return FilterResult(null, true)
        } else {
            if (appSearchIndexDirty || appSearchIndex.size != updatedApps.size) {
                appSearchIndex = buildAppSearchIndex(updatedApps)
                appSearchIndexDirty = false
            }

            val queryLower = cleanQuery.lowercase()

            val fuzzyPattern = if (sharedPreferenceManager.isFuzzySearchEnabled()) {
                stringUtils.getFuzzyPattern(cleanQuery)
            } else {
                null
            }

            val exactMatches = mutableListOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
            val otherMatches = mutableListOf<Triple<LauncherActivityInfo, UserHandle, Int>>()

            for (entry in appSearchIndex) {
                if (entry.cleaned.isEmpty()) continue

                val fuzzyMatch = fuzzyPattern?.containsMatchIn(entry.cleaned) == true
                val contains = entry.cleanedLower.contains(queryLower)
                if (!contains && !fuzzyMatch) continue

                if (entry.cleanedLower == queryLower) {
                    exactMatches.add(entry.item)
                } else {
                    otherMatches.add(entry.item)
                }
            }

            return FilterResult(exactMatches + otherMatches, false)
        }
    }

    private suspend fun applySearchFilter(newFilteredApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        if (sharedPreferenceManager.isAutoLaunchEnabled() && menuView.displayedChild == 0 && appAdapter?.shortcutTextView == null && newFilteredApps.size == 1) {
            appUtils.launchApp(newFilteredApps[0].first.componentName, newFilteredApps[0].second)
        } else {
            updateMenu(newFilteredApps)
            currentFilteredApps = newFilteredApps
            refreshAlphabetIndex(currentFilteredApps)
        }
    }

    suspend fun applySearch() {
        // Cancel any ongoing search job
        searchJob?.cancel()
        withContext(Dispatchers.Default) {
            filterItems(searchView.text.toString())
        }
    }

    fun disableAppMenuScroll() {
        appMenuLinearLayoutManager.setScrollEnabled(false)
        appRecycler.layoutManager = appMenuLinearLayoutManager
    }

    fun enableAppMenuScroll() {
        appMenuLinearLayoutManager.setScrollEnabled(true)
        appRecycler.layoutManager = appMenuLinearLayoutManager
    }

    // On home key or swipe, return to home screen
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        backToHome()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.homeView.isVisible && event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            val unicodeChar = event.unicodeChar

            if (unicodeChar != 0 && !KeyEvent.isModifierKey(keyCode)) {
                val char = unicodeChar.toChar()
                if (char.isLetterOrDigit()) {
                    openAppMenuWithChar(char.toString())
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openAppMenuWithChar(char: String) {
        appAdapter?.shortcutTextView = null
        contactAdapter?.shortcutTextView = null
        menuTitle.visibility = View.GONE
        uiUtils.setWebSearchVisibility(internetSearch)

        searchView.setText(char)
        searchView.setSelection(searchView.text?.length ?: 0)

        toAppMenuWithKeyboard()
    }

    private fun toAppMenuWithKeyboard() {
        uiUtils.setContactsVisibility(searchSwitcher, binding.searchLayout, binding.searchReplacement)

        try {
            appRecycler.scrollToPosition(0)
            menuView.displayedChild = 0
            if (searchSwitcher.isVisible) {
                contactRecycler.scrollToPosition(0)
                setAppViewDetails()
            }
        } catch (_: UninitializedPropertyAccessException) {
        }
        animations.showApps(binding.homeView, binding.appView)
        animations.backgroundIn(this@MainActivity)

        searchView.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterBatteryReceiver()
        unregisterNotificationReceiver()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        searchJob?.cancel()
        logger.i("MainActivity", "MainActivity destroyed")
    }

    private fun registerNotificationReceiver() {
        if (!isNotificationReceiverRegistered) {
            val filter = android.content.IntentFilter(NotificationListener.ACTION_NOTIFICATIONS_CHANGED)
            LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, filter)
            isNotificationReceiverRegistered = true
        }
    }

    private fun unregisterNotificationReceiver() {
        if (isNotificationReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
            isNotificationReceiverRegistered = false
        }
    }

    private val notificationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNotificationDots()
        }
    }

    private fun updateNotificationDots() {
        if (!sharedPreferenceManager.isNotificationDotsEnabled()) return

        val notificationListener = NotificationListener.getInstance()
        if (notificationListener == null) {
            if (!NotificationListener.isEnabled(this)) {
                requestNotificationPermission()
            }
            return
        }

        val packagesWithNotifications = notificationListener.getPackagesWithNotifications()
        val shortcuts = arrayOf(
            R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5,
            R.id.app6, R.id.app7, R.id.app8, R.id.app9, R.id.app10,
            R.id.app11, R.id.app12, R.id.app13, R.id.app14, R.id.app15
        )

        for (i in shortcuts.indices) {
            val textView = findViewById<TextView>(shortcuts[i])
            val savedView = sharedPreferenceManager.getShortcut(i)

            if (savedView != null && savedView.getOrNull(3)?.toBoolean() != true) {
                val componentName = savedView[0]
                if (componentName != "e") {
                    val packageName = if (componentName.contains("/")) {
                        componentName.substringBefore("/")
                    } else {
                        componentName
                    }

                    val hasNotification = packagesWithNotifications.contains(packageName)
                    val dotDrawable = if (hasNotification) {
                        ResourcesCompat.getDrawable(resources, R.drawable.notification_dot, null)
                    } else {
                        null
                    }

                    dotDrawable?.setTint(sharedPreferenceManager.getTextColor())
                    textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        textView.compoundDrawablesRelative[0],
                        null,
                        dotDrawable,
                        null
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        NotificationListener.requestPermission(this)
    }

    override fun onStart() {
        super.onStart()
        // Keyboard is sometimes open when going back to the app, so close it.
        closeKeyboard()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if (!permissionUtils.hasPermission(this@MainActivity, Manifest.permission.READ_CONTACTS)) {
            sharedPreferenceManager.setContactsEnabled(false)
        }
        if (!permissionUtils.hasPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            sharedPreferenceManager.setWeatherGPS(false)
        }
        if (returnAllowed) {
            backToHome(0)
        }
        returnAllowed = true
        appAdapter?.notifyDataSetChanged()

        updateNotificationDots()

        updateDefaultLauncherBanner()
    }

    private fun updateDefaultLauncherBanner() {
        val defaultLauncherBanner = findViewById<LinearLayout>(R.id.defaultLauncherBanner)
        if (!isDefaultLauncher()) {
            defaultLauncherBanner?.visibility = View.VISIBLE
        } else {
            defaultLauncherBanner?.visibility = View.GONE
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val packageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    override fun onItemClick(appInfo: LauncherActivityInfo, userHandle: UserHandle) {
        appUtils.launchApp(appInfo.componentName, userHandle)
    }

    override fun onShortcut(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        textView: TextView,
        userProfile: Int,
        shortcutView: TextView,
        shortcutIndex: Int
    ) {
        if (userProfile != 0) {
            shortcutView.setCompoundDrawablesWithIntrinsicBounds(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_work_app, null),
                null,
                null,
                null
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                shortcutView.compoundDrawables[0]?.colorFilter =
                    BlendModeColorFilter(sharedPreferenceManager.getTextColor(), BlendMode.SRC_ATOP)
                shortcutView.compoundDrawables[2]?.colorFilter =
                    BlendModeColorFilter(sharedPreferenceManager.getTextColor(), BlendMode.SRC_ATOP)
            } else {
                shortcutView.compoundDrawables[0]?.colorFilter =
                    PorterDuffColorFilter(sharedPreferenceManager.getTextColor(), PorterDuff.Mode.SRC_ATOP)
                shortcutView.compoundDrawables[2]?.colorFilter =
                    PorterDuffColorFilter(sharedPreferenceManager.getTextColor(), PorterDuff.Mode.SRC_ATOP)
            }
        } else {
            shortcutView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null), null, null, null)
        }

        shortcutView.text = textView.text.toString()
        shortcutView.setOnClickListener {
            appUtils.launchApp(appInfo.componentName, userHandle)
        }
        sharedPreferenceManager.setShortcut(
            shortcutIndex,
            shortcutView.text,
            appInfo.componentName.flattenToString(),
            userProfile
        )
        uiUtils.setDrawables(shortcutView, sharedPreferenceManager.getShortcutAlignment())
        backToHome()
    }

    override fun onItemLongClick(
        appInfo: LauncherActivityInfo,
        userHandle: UserHandle,
        userProfile: Int
    ) {
        val bottomSheet = AppActionBottomSheet.newInstance(appInfo, userHandle, userProfile, this)
        bottomSheet.show(supportFragmentManager, AppActionBottomSheet.TAG)
    }

    override fun onPinApp(appActivity: LauncherActivityInfo, workProfile: Int) {
        val componentName = appActivity.componentName.flattenToString()
        sharedPreferenceManager.setPinnedApp(componentName, workProfile)
        val isPinned = sharedPreferenceManager.isAppPinned(componentName, workProfile)
        logger.i("MainActivity", "App ${appActivity.label} ${if (isPinned) "pinned" else "unpinned"}")
        lifecycleScope.launch {
            refreshAppMenu()
        }
    }

    override fun onAppInfo(appActivity: LauncherActivityInfo, userHandle: UserHandle) {
        launcherApps.startAppDetailsActivity(
            appActivity.componentName,
            userHandle,
            null,
            null
        )
    }

    override fun onUninstallApp(appActivity: LauncherActivityInfo, userHandle: UserHandle) {
        logger.i("MainActivity", "Uninstalling app: ${appActivity.applicationInfo.packageName}")
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:${appActivity.applicationInfo.packageName}")
        intent.putExtra(Intent.EXTRA_USER, userHandle)
        startActivity(intent)
        returnAllowed = false
    }

    override fun onRenameApp(appActivity: LauncherActivityInfo, userHandle: UserHandle, workProfile: Int) {
        // Find the view holder for this app and trigger rename mode
        val position = findAppPosition(appActivity, workProfile)
        if (position != -1) {
            val viewHolder = appRecycler.findViewHolderForAdapterPosition(position) as? AppMenuAdapter.AppViewHolder
            if (viewHolder != null) {
                startRenameMode(viewHolder.textView, viewHolder.editView, appActivity, userHandle, workProfile)
            } else {
                // View holder is null (view recycled), scroll to position and try again
                appRecycler.layoutManager?.scrollToPosition(position)
                appRecycler.post {
                    val retryViewHolder = appRecycler.findViewHolderForAdapterPosition(position) as? AppMenuAdapter.AppViewHolder
                    if (retryViewHolder != null) {
                        startRenameMode(retryViewHolder.textView, retryViewHolder.editView, appActivity, userHandle, workProfile)
                    } else {
                        // Still can't get view holder, show error toast
                        Toast.makeText(this@MainActivity, getString(R.string.rename_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private var renameLayoutListener: View.OnLayoutChangeListener? = null

    private fun startRenameMode(textView: TextView, editLayout: LinearLayout, appActivity: LauncherActivityInfo, userHandle: UserHandle, workProfile: Int) {
        disableAppMenuScroll()
        textView.visibility = View.INVISIBLE
        animations.fadeViewIn(editLayout)
        val editText = editLayout.findViewById<EditText>(R.id.appNameEdit)
        val resetButton = editLayout.findViewById<AppCompatButton>(R.id.reset)

        val searchEnabled = sharedPreferenceManager.isSearchEnabled()

        if (searchEnabled) {
            searchView.visibility = View.INVISIBLE
        } else {
            searchView.visibility = View.GONE
        }

        editText.requestFocus()

        // Open keyboard
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val imm =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        renameLayoutListener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->

            // If the keyboard is closed, exit editing mode
            if (bottom - top > oldBottom - oldTop) {
                editLayout.clearFocus()

                animations.fadeViewOut(editLayout)
                animations.fadeViewIn(textView)
                if (searchEnabled) {
                    searchView.visibility = View.VISIBLE
                } else {
                    searchView.visibility = View.GONE
                }
                enableAppMenuScroll()

                // Remove the listener and clear the reference
                renameLayoutListener?.let { listener ->
                    binding.root.removeOnLayoutChangeListener(listener)
                }
                renameLayoutListener = null
            }
        }

        binding.root.addOnLayoutChangeListener(renameLayoutListener!!)

        editText.setOnEditorActionListener { _, actionId, _ ->

            // Once the new name is confirmed, close the keyboard, save the new app name and update the apps on screen
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (editText.text.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, getString(R.string.empty_rename), Toast.LENGTH_SHORT).show()
                    return@setOnEditorActionListener true
                }
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
                val newName = editText.text.toString()
                sharedPreferenceManager.setAppName(
                    appActivity.componentName.flattenToString(),
                    workProfile,
                    newName
                )
                appSearchIndexDirty = true
                logger.i("MainActivity", "App renamed from '${appActivity.label}' to '$newName'")
                lifecycleScope.launch {
                    applySearch()
                }
                enableAppMenuScroll()

                // Remove the layout listener when done
                renameLayoutListener?.let { listener ->
                    binding.root.removeOnLayoutChangeListener(listener)
                }
                renameLayoutListener = null

                return@setOnEditorActionListener true
            }
            false
        }

        resetButton.setOnClickListener {

            // If reset is pressed, close keyboard, remove saved edited name and update the apps on screen
            val imm =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
            sharedPreferenceManager.resetAppName(
                appActivity.componentName.flattenToString(),
                workProfile
            )
            appSearchIndexDirty = true

            lifecycleScope.launch {
                applySearch()
            }
        }
    }

    private fun findAppPosition(appActivity: LauncherActivityInfo, workProfile: Int): Int {
        val appsToSearch = currentFilteredApps
        for (i in 0 until appsToSearch.size) {
            val tuple = appsToSearch.getOrNull(i)
            if (tuple?.first?.componentName == appActivity.componentName && tuple.third == workProfile) {
                return i
            }
        }
        return -1
    }

    override fun onHideApp(appActivity: LauncherActivityInfo, workProfile: Int) {
        logger.i("MainActivity", "Hiding app: ${appActivity.label}")
        lifecycleScope.launch {
            sharedPreferenceManager.setAppHidden(appActivity.componentName.flattenToString(), workProfile, true)
            refreshAppMenu()
        }
    }

    open inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @SuppressLint("WrongConstant")
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (animations.isInAnim) {
                return false
            }
            if (e1 != null) {
                val deltaY = e2.y - e1.y
                val deltaX = e2.x - e1.x

                // Swipe up
                if (deltaY < -swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                    canLaunchShortcut = false
                    openAppMenu()
                }

                // Swipe down
                else if (deltaY > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                    val statusBarService = getSystemService(STATUS_BAR_SERVICE)
                    val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
                    val expandMethod: Method = statusBarManager.getMethod("expandNotificationsPanel")
                    expandMethod.invoke(statusBarService)
                }

                // Swipe left
                else if (deltaX < 0 && abs(deltaX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold && sharedPreferenceManager.isGestureEnabled(
                        "left"
                    )
                ) {
                    if (leftSwipeActivity.first != null && leftSwipeActivity.second != null) {
                        canLaunchShortcut = false
                        appUtils.launchApp(leftSwipeActivity.first!!.componentName, launcherApps.profiles[leftSwipeActivity.second!!])
                    } else {
                        logger.w("MainActivity", "Left swipe gesture failed: no app configured")
                        Toast.makeText(this@MainActivity, getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    }
                }


                // Swipe right
                else if (deltaX > 0 && abs(deltaX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold && sharedPreferenceManager.isGestureEnabled(
                        "right"
                    )
                ) {
                    if (rightSwipeActivity.first != null && rightSwipeActivity.second != null) {
                        canLaunchShortcut = false
                        appUtils.launchApp(rightSwipeActivity.first!!.componentName, launcherApps.profiles[rightSwipeActivity.second!!])
                    } else {
                        logger.w("MainActivity", "Right swipe gesture failed: no app configured")
                        Toast.makeText(this@MainActivity, getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            trySettings()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (sharedPreferenceManager.isDoubleTapEnabled()) {
                when (sharedPreferenceManager.getDoubleTapAction()) {
                    "app" -> {
                        if (doubleTapApp.first != null && doubleTapApp.second != null) {
                            appUtils.launchApp(doubleTapApp.first!!.componentName, launcherApps.profiles[doubleTapApp.second!!])
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.launch_error), Toast.LENGTH_SHORT).show()
                        }
                    }

                    else -> {
                        if (gestureUtils.isAccessibilityServiceEnabled(
                                ScreenLockService::class.java
                            )
                        ) {
                            val intent = Intent(this@MainActivity, ScreenLockService::class.java)
                            intent.action = "LOCK_SCREEN"
                            startService(intent)
                        } else {
                            gestureUtils.promptEnableAccessibility()
                        }
                    }
                }
            }

            return super.onDoubleTap(e)

        }

    }

    inner class TextGestureListener : GestureListener() {
        override fun onLongPress(e: MotionEvent) {

        }
    }

    override fun onContactClick(contactId: Int) {
        val contactUri: Uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI,
            contactId.toString()
        )

        val intent = Intent(Intent.ACTION_VIEW, contactUri)
        startActivity(intent)
        returnAllowed = false
    }

    override fun onContactShortcut(contactId: Int, contactName: String, shortcutView: TextView, shortcutIndex: Int) {
        shortcutView.text = contactName
        shortcutView.setOnClickListener {
            onContactClick(contactId)
        }
        sharedPreferenceManager.setShortcut(
            shortcutIndex,
            shortcutView.text,
            contactName,
            contactId,
            true
        )
        shortcutView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, null), null, null, null)
        uiUtils.setDrawables(shortcutView, sharedPreferenceManager.getShortcutAlignment())
        backToHome()
    }
}
