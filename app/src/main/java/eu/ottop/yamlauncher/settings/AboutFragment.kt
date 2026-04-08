package eu.ottop.yamlauncher.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import eu.ottop.yamlauncher.R
import eu.ottop.yamlauncher.utils.StringUtils

/**
 * About fragment displaying app information and links.
 * Shows version, links to source code, and distribution platforms.
 */
class AboutFragment : Fragment(), TitleProvider {

    private val stringUtils = StringUtils()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val launcherApps = requireActivity().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Set up clickable links
        stringUtils.setLink(requireActivity().findViewById(R.id.githubLink), getString(R.string.github_link))
        stringUtils.setLink(requireActivity().findViewById(R.id.fdroidLink), getString(R.string.fdroid_link))
        stringUtils.setLink(requireActivity().findViewById(R.id.izzyLink), getString(R.string.izzy_link))
        stringUtils.setLink(requireActivity().findViewById(R.id.playLink), getString(R.string.play_link))
        stringUtils.setLink(requireActivity().findViewById(R.id.donateLink), getString(R.string.ko_fi_link))

        // Display app version
        val currentVersion = "v" + requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0).versionName
        requireActivity().findViewById<TextView>(R.id.version).text = currentVersion
    }

    override fun getTitle(): String {
        return getString(R.string.about_title)
    }
}
