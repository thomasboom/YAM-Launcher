package eu.ottop.yamlauncher.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.ottop.yamlauncher.MainActivity

/**
 * Custom LinearLayoutManager that adds home screen navigation gestures.
 *
 * Features:
 * - Scroll direction detection for navigating back to home
 * - Configurable scroll enable/disable (used during edit modes)
 * - Overscroll detection to trigger home navigation
 */
class AppMenuLinearLayoutManager(private val activity: MainActivity) : LinearLayoutManager(activity) {

    // Tracks the first visible item for overscroll detection
    private var firstVisibleItemPosition = 0
    private var scrollStarted = false

    // Controls whether vertical scrolling is allowed
    // Disabled during rename mode to prevent accidental scrolling
    private var isScrollEnabled: Boolean = true

    /**
     * Enables or disables vertical scrolling.
     * Used during edit modes (like rename) to prevent accidental navigation.
     *
     * @param enabled true to allow scrolling, false to disable
     */
    fun setScrollEnabled(enabled: Boolean) {
        isScrollEnabled = enabled
    }

    /**
     * Controls vertical scrolling based on isScrollEnabled flag.
     * Disabling scroll prevents user interaction while editing.
     */
    override fun canScrollVertically(): Boolean {
        return isScrollEnabled && super.canScrollVertically()
    }

    /**
     * Records scroll state information for overscroll detection.
     * Called when user starts dragging the list.
     */
    fun setScrollInfo() {
        firstVisibleItemPosition = findFirstCompletelyVisibleItemPosition()
        scrollStarted = true
    }

    /**
     * Handles vertical scroll operations with overscroll detection.
     * If user scrolls up while at top of list, navigates back to home.
     *
     * @param dy Scroll delta in pixels
     * @param recycler RecyclerView's recycler for view recycling
     * @param state Current scroll state
     * @return Actual scroll distance that occurred
     */
    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        val scrollRange = super.scrollVerticallyBy(dy, recycler, state)
        val overscroll: Int = dy - scrollRange

        // If user scrolls up (negative dy) when already at top, go back to home
        // Only triggers if keyboard isn't open (firstVisibleItemPosition valid)
        if (overscroll < 0 && (firstVisibleItemPosition == 0 || firstVisibleItemPosition < 0) && scrollStarted) {
            activity.backToHome()
        }

        if (scrollStarted) {
            scrollStarted = false
        }

        return scrollRange
    }

}
