package eu.ottop.yamlauncher.settings

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import eu.ottop.yamlauncher.utils.AppMenuEdgeFactory
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.AppNameResolver
import eu.ottop.yamlauncher.utils.AppUtils
import eu.ottop.yamlauncher.utils.StringUtils
import eu.ottop.yamlauncher.utils.UIUtils
import kotlinx.coroutines.launch

/**
 * Fragment for selecting an app to assign to a gesture.
 * Used for configuring swipe gestures and tap actions.
 *
 * @param direction Which gesture is being configured
 */
class GestureAppsFragment(private val direction: String) : Fragment(),
    GestureAppsAdapter.OnItemClickListener, TitleProvider {

    private var adapter: GestureAppsAdapter? = null
    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private var stringUtils = StringUtils()
    private lateinit var appUtils: AppUtils
    private lateinit var launcherApps: LauncherApps

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gesture_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        appUtils = AppUtils(requireContext(), launcherApps)
        sharedPreferenceManager = SharedPreferenceManager(requireContext())

        // Load all apps (including hidden) for gesture assignment
        lifecycleScope.launch {
            adapter = GestureAppsAdapter(
                requireContext(),
                appUtils.getInstalledApps(true).toMutableList(),
                this@GestureAppsFragment
            )

            val recyclerView = view.findViewById<RecyclerView>(R.id.gestureAppRecycler)
            val appMenuEdgeFactory = AppMenuEdgeFactory(requireActivity())
            val uiUtils = UIUtils(requireContext())

            recyclerView.edgeEffectFactory = appMenuEdgeFactory
            recyclerView.adapter = adapter
            recyclerView.scrollToPosition(0)

            // Set up search
            val searchView = view.findViewById<TextInputEditText>(R.id.gestureAppSearch)

            uiUtils.setSearchAlignment(searchView)
            uiUtils.setSearchSize(searchView)

            // Hide keyboard when list scrolls
            recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top > oldBottom - oldTop) {
                    searchView.clearFocus()
                }
            }

            // Search with filtering
            searchView.addTextChangedListener(object :
                TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    lifecycleScope.launch {
                        filterItems(s.toString())
                    }

                }
            })
        }
    }

    /**
     * Filters apps based on search query.
     */
    private suspend fun filterItems(query: String?) {

        val cleanQuery = stringUtils.cleanString(query)
        val newFilteredApps = mutableListOf<Triple<LauncherActivityInfo, UserHandle, Int>>()
        val updatedApps = appUtils.getInstalledApps(true)

        getFilteredApps(cleanQuery, newFilteredApps, updatedApps)

        applySearch(newFilteredApps)

    }

    /**
     * Applies fuzzy or exact matching filter.
     */
    private fun getFilteredApps(cleanQuery: String?, newFilteredApps: MutableList<Triple<LauncherActivityInfo, UserHandle, Int>>, updatedApps: List<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        if (cleanQuery.isNullOrEmpty()) {
            newFilteredApps.addAll(updatedApps)
        } else {
            val fuzzyPattern = if(sharedPreferenceManager.isFuzzySearchEnabled()) {
                stringUtils.getFuzzyPattern(cleanQuery)
            }
            else {
                null
            }
            updatedApps.forEach {
                val cleanItemText = stringUtils.cleanString(sharedPreferenceManager.getAppName(
                    it.first.componentName.flattenToString(),
                    it.third,
                    AppNameResolver.resolveBaseLabel(requireContext(), it.first)
                ).toString())
                if (cleanItemText != null) {
                    if (
                        (fuzzyPattern != null && cleanItemText.contains(fuzzyPattern)) ||
                        (cleanItemText.contains(cleanQuery, ignoreCase = true))
                    ) {
                        newFilteredApps.add(it)
                    }
                }
            }
        }
    }

    private fun applySearch(newFilteredApps: MutableList<Triple<LauncherActivityInfo, UserHandle, Int>>) {
        adapter?.updateApps(newFilteredApps)
    }

    /**
     * Shows confirmation before saving gesture.
     */
    private fun showConfirmationDialog(appInfo: LauncherActivityInfo, appName: String, profile: Int) {
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(getString(R.string.confirm_title))
            setMessage("${getString(R.string.app_confirm_text)} $appName?")

            setPositiveButton(getString(R.string.confirm_yes)) { _, _ ->
                performConfirmedAction(appInfo, appName, profile)
            }

            setNegativeButton(getString(R.string.confirm_no)) { _, _ ->
            }

        }.create().show()
    }

    /**
     * Saves gesture configuration and returns.
     */
    private fun performConfirmedAction(appInfo: LauncherActivityInfo, appName: String, profile: Int) {
        sharedPreferenceManager.setGestures(
            direction, "$appName§splitter§${appInfo.componentName.flattenToString()}§splitter§$profile"
        )
        requireActivity().supportFragmentManager.popBackStack()
    }


    override fun onItemClick(appInfo: LauncherActivityInfo, profile: Int) {
        showConfirmationDialog(appInfo, sharedPreferenceManager.getAppName(
            appInfo.componentName.flattenToString(),
            profile,
            AppNameResolver.resolveBaseLabel(requireContext(), appInfo)
        ).toString(), profile)
    }

    override fun getTitle(): String {
        return getString(R.string.select_an_app)
    }

}
