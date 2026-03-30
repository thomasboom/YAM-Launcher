package eu.ottop.yamlauncher

import android.app.Dialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.ottop.yamlauncher.settings.SharedPreferenceManager
import eu.ottop.yamlauncher.utils.AppNameResolver

/**
 * Bottom sheet dialog for app actions.
 * Provides options to pin, view info, uninstall, rename, or hide apps.
 *
 * Actions can be individually enabled/disabled via preferences.
 */
class AppActionBottomSheet : BottomSheetDialogFragment() {

    /**
     * Listener interface for app action callbacks.
     * Implement to handle each action type.
     */
    interface AppActionListener {
        /** Toggle app pin status */
        fun onPinApp(appActivity: LauncherActivityInfo, workProfile: Int)
        /** Open app info screen */
        fun onAppInfo(appActivity: LauncherActivityInfo, userHandle: UserHandle)
        /** Initiate app uninstall */
        fun onUninstallApp(appActivity: LauncherActivityInfo, userHandle: UserHandle)
        /** Enter rename mode */
        fun onRenameApp(appActivity: LauncherActivityInfo, userHandle: UserHandle, workProfile: Int)
        /** Hide app from launcher */
        fun onHideApp(appActivity: LauncherActivityInfo, workProfile: Int)
    }

    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private lateinit var appNameTitle: TextView
    private lateinit var pinButton: LinearLayout
    private lateinit var pinIcon: ImageView
    private lateinit var pinLabel: TextView

    private var appActivity: LauncherActivityInfo? = null
    private var userHandle: UserHandle? = null
    private var workProfile: Int = 0
    private var listener: AppActionListener? = null

    companion object {
        const val TAG = "AppActionBottomSheet"

        // Argument keys for fragment arguments
        private const val ARG_COMPONENT_NAME = "component_name"
        private const val ARG_USER_HANDLE_ID = "user_handle_id"
        private const val ARG_WORK_PROFILE = "work_profile"

        /**
         * Creates a new instance with app data.
         *
         * @param appActivity The app to show actions for
         * @param userHandle User profile handle
         * @param workProfile Profile index (0 = personal, 1+ = work)
         * @param listener Callback for actions
         * @return New fragment instance
         */
        fun newInstance(
            appActivity: LauncherActivityInfo,
            userHandle: UserHandle,
            workProfile: Int,
            listener: AppActionListener
        ): AppActionBottomSheet {
            val fragment = AppActionBottomSheet()
            fragment.listener = listener
            val args = Bundle().apply {
                putString(ARG_COMPONENT_NAME, appActivity.componentName.flattenToString())
                putInt(ARG_USER_HANDLE_ID, workProfile)
                putInt(ARG_WORK_PROFILE, workProfile)
            }
            fragment.arguments = args
            fragment.appActivity = appActivity
            fragment.userHandle = userHandle
            fragment.workProfile = workProfile
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.Theme_YamLauncher_BottomSheet)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.app_action_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferenceManager = SharedPreferenceManager(requireContext())

        // Restore app data if fragment was recreated
        if (appActivity == null) {
            val componentNameStr = arguments?.getString(ARG_COMPONENT_NAME)
            val userHandleId = arguments?.getInt(ARG_USER_HANDLE_ID, 0) ?: 0
            workProfile = arguments?.getInt(ARG_WORK_PROFILE, 0) ?: 0

            if (componentNameStr != null) {
                val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                userHandle = launcherApps.profiles.getOrNull(userHandleId)
                val componentName = android.content.ComponentName.unflattenFromString(componentNameStr)
                appActivity = componentName?.let { cn ->
                    userHandle?.let { uh ->
                        // Find the specific activity in the package
                        launcherApps.getActivityList(cn.packageName, uh).firstOrNull { it.componentName == cn }
                    }
                }
            }
        }

        // Get current values
        val currentApp = appActivity
        val currentUser = userHandle
        val currentListener = listener

        // Validate we have all required data
        if (currentApp == null || currentUser == null || currentListener == null) {
            dismiss()
            return
        }

        // Set app title with custom name if set
        appNameTitle = view.findViewById(R.id.appNameTitle)
        appNameTitle.text = sharedPreferenceManager.getAppName(
            currentApp.componentName.flattenToString(),
            workProfile,
            AppNameResolver.resolveBaseLabel(requireContext(), currentApp)
        )

        setupActionButtons(view, currentApp, currentUser, currentListener)
    }

    /**
     * Sets up action buttons based on enabled preferences.
     * Shows/hides buttons and sets click listeners.
     */
    private fun setupActionButtons(view: View, appActivity: LauncherActivityInfo, userHandle: UserHandle, listener: AppActionListener) {
        // Get button views
        pinButton = view.findViewById(R.id.pin)
        pinIcon = pinButton.findViewById(R.id.pinIcon)
        pinLabel = pinButton.findViewById(R.id.pinLabel)
        val infoButton = view.findViewById<LinearLayout>(R.id.info)
        val uninstallButton = view.findViewById<LinearLayout>(R.id.uninstall)
        val renameButton = view.findViewById<LinearLayout>(R.id.rename)
        val hideButton = view.findViewById<LinearLayout>(R.id.hide)

        // Check which actions are enabled in preferences
        val enablePin = sharedPreferenceManager.isPinEnabled()
        val enableInfo = sharedPreferenceManager.isInfoEnabled()
        val enableUninstall = sharedPreferenceManager.isUninstallEnabled()
        val enableRename = sharedPreferenceManager.isRenameEnabled()
        val enableHide = sharedPreferenceManager.isHideEnabled()

        // Set initial pin state
        setPinState(appActivity)

        // Setup each button with appropriate conditions
        setupButton(pinButton, enablePin) {
            listener.onPinApp(appActivity, workProfile)
        }

        setupButton(infoButton, enableInfo) {
            listener.onAppInfo(appActivity, userHandle)
        }

        // Uninstall only available for non-system apps
        setupButton(uninstallButton, enableUninstall, appActivity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            listener.onUninstallApp(appActivity, userHandle)
        }

        setupButton(renameButton, enableRename) {
            listener.onRenameApp(appActivity, userHandle, workProfile)
        }

        setupButton(hideButton, enableHide) {
            listener.onHideApp(appActivity, workProfile)
        }
    }

    /**
     * Configures a single action button.
     *
     * @param button Button view
     * @param enabled Whether button should be visible
     * @param additionalCondition Extra condition for visibility
     * @param action Lambda to execute on click
     */
    private fun setupButton(button: View, enabled: Boolean, additionalCondition: Boolean = true, action: () -> Unit) {
        if (enabled && additionalCondition) {
            button.visibility = View.VISIBLE
            button.setOnClickListener {
                action()
                dismiss()
            }
        } else {
            button.visibility = View.GONE
        }
    }

    override fun onDetach() {
        super.onDetach()
        // Clear listener reference to prevent memory leaks
        listener = null
    }

    /**
     * Updates pin button icon and label based on current pin state.
     */
    private fun setPinState(appActivity: LauncherActivityInfo) {
        val isPinned = sharedPreferenceManager.isAppPinned(
            appActivity.componentName.flattenToString(),
            workProfile
        )

        // Toggle icon between filled (pinned) and outline (not pinned)
        val iconRes = when (isPinned) {
            true -> R.drawable.keep_off_24px
            false -> R.drawable.keep_24px
        }
        pinIcon.setImageResource(iconRes)

        // Toggle label between "Unpin" and "Pin"
        val labelText = when (isPinned) {
            true -> getString(R.string.unpin)
            false -> getString(R.string.pin)
        }
        pinLabel.text = labelText
    }
}
