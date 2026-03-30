package eu.ottop.yamlauncher.utils

import eu.ottop.yamlauncher.R

/**
 * Font registry mapping font identifiers to font resources.
 * Centralizes custom font loading for consistent typography.
 *
 * Available fonts:
 * - arbutus: Decorative display font
 * - ubuntu: Clean sans-serif font
 * - ubuntu_light: Light weight variant of Ubuntu
 * - ubuntu_condensed_regular: Condensed variant for space-constrained areas
 * - workbench: Technical/developer style font
 *
 * Maps font key strings to their corresponding R.font resource IDs.
 */
object FontMap {
    /**
     * Map of font identifier strings to font resource IDs.
     * Fonts are loaded dynamically via ResourcesCompat.getFont().
     */
    val fonts: Map<String, Int> = mapOf(
        "arbutus" to R.font.arbutus,
        "ubuntu" to R.font.ubuntu,
        "ubuntu_light" to R.font.ubuntu_light,
        "ubuntu_condensed_regular" to R.font.ubuntu_condensed_regular,
        "workbench" to R.font.workbench,
    )
}
