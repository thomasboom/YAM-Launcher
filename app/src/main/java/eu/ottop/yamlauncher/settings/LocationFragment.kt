package eu.ottop.yamlauncher.settings

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import eu.ottop.yamlauncher.utils.AppMenuEdgeFactory
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.WeatherSystem
import eu.ottop.yamlauncher.utils.StringUtils
import eu.ottop.yamlauncher.utils.UIUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationFragment : Fragment(), LocationListAdapter.OnItemClickListener, TitleProvider {

    private var adapter: LocationListAdapter? = null
    private lateinit var weatherSystem: WeatherSystem
    private lateinit var sharedPreferenceManager: SharedPreferenceManager
    private val stringUtils = StringUtils()
    private lateinit var uiUtils: UIUtils

    private var searchJob: Job? = null
    private var searchSeq: Int = 0
    private var lastSubmittedQuery: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_location, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiUtils = UIUtils(requireContext())
        weatherSystem = WeatherSystem(requireContext())
        sharedPreferenceManager = SharedPreferenceManager(requireContext())

        val searchView = view.findViewById<TextInputEditText>(R.id.locationSearch)
        val locationLink = view.findViewById<TextView>(R.id.locationLink)
        stringUtils.setLink(locationLink, getString(R.string.location_link))

        adapter = LocationListAdapter(requireContext(), mutableListOf(), this)
        val recyclerView = view.findViewById<RecyclerView>(R.id.locationRecycler)
        val appMenuEdgeFactory = AppMenuEdgeFactory(requireActivity())
        uiUtils.setSearchAlignment(searchView)
        uiUtils.setSearchSize(searchView)

        recyclerView.edgeEffectFactory = appMenuEdgeFactory
        recyclerView.adapter = adapter

        recyclerView.scrollToPosition(0)



        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->

            if (bottom - top > oldBottom - oldTop) {
                searchView.clearFocus()
            }
        }

        searchView.addTextChangedListener(object :
            TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val query = searchView.text?.toString()?.trim().orEmpty()

                searchJob?.cancel()

                if (query.length < 2) {
                    lastSubmittedQuery = null
                    adapter?.updateLocations(mutableListOf())
                    return
                }

                if (query == lastSubmittedQuery) return
                lastSubmittedQuery = query

                val seq = ++searchSeq
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(350)
                    val locations = withContext(Dispatchers.IO) {
                        weatherSystem.getSearchedLocations(query)
                    }
                    if (seq != searchSeq) return@launch
                    adapter?.updateLocations(locations)
                }
            }
        })

        if (sharedPreferenceManager.isAutoKeyboardEnabled()) {
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            searchView.requestFocus()
            imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        searchJob = null
        adapter = null
        super.onDestroyView()
    }

    private fun showConfirmationDialog(locationName: String?, latitude: String?, longitude: String?) {
        if (locationName == null || latitude == null || longitude == null) {
            return
        }
        MaterialAlertDialogBuilder(
            ContextThemeWrapper(
                requireContext(),
                com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar
            )
        ).apply {
            setTitle(getString(R.string.confirm_title))
            setMessage("${getString(R.string.app_confirm_text)} $locationName?")
            setPositiveButton(getString(R.string.confirm_yes)) { _, _ ->
                performConfirmedAction(locationName, latitude, longitude)
            }
            setNegativeButton(R.string.confirm_no) { _, _ ->

            }

        }.create().show()
    }

    private fun performConfirmedAction(locationName: String, latitude: String, longitude: String) {
        sharedPreferenceManager.setWeatherLocation("latitude=${latitude}&longitude=${longitude}", locationName)
        requireActivity().supportFragmentManager.popBackStack()
    }


    override fun onItemClick(name: String?, latitude: String?, longitude: String?) {
        showConfirmationDialog(name, latitude, longitude)
    }

    override fun getTitle(): String {
        return getString(R.string.find_your_city)
    }

}
