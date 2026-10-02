package dev.alllexey.itmowidgets.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.R

/**
 * Stands in for root tabs a test does not exercise, so nothing there touches the network.
 * A column three screens tall gives the tab a scroll position that its saved state must keep.
 */
class BlankTabFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ScrollView(requireContext()).apply {
            id = R.id.blank_tab_scroll
            // ScrollView measures its child without a height limit, so the height is the child's minimum.
            addView(View(context).apply { minimumHeight = resources.displayMetrics.heightPixels * 3 })
        }
}
