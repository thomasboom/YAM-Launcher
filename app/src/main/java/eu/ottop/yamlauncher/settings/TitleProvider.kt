package eu.ottop.yamlauncher.settings

/**
 * Interface for fragments that provide a title.
 * Used by SettingsActivity to update the action bar title.
 */
interface TitleProvider {
    /**
     * Returns the title string for this fragment.
     *
     * @return Fragment title
     */
    fun getTitle(): String
}
