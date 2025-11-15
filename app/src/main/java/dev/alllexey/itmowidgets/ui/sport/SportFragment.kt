package dev.alllexey.itmowidgets.ui.sport

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.alllexey.itmowidgets.R

class SportFragment : Fragment(R.layout.fragment_sport) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById<ViewPager2>(R.id.sport_view_pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.sport_tab_layout)

        viewPager.adapter = SportPagerAdapter(requireActivity())
        viewPager.isUserInputEnabled = false
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_sport_me)
                1 -> getString(R.string.title_sport_sign)
                2 -> getString(R.string.title_sport_notifications)
                else -> null
            }
        }.attach()
    }
}