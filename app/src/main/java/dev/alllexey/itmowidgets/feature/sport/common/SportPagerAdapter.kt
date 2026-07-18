package dev.alllexey.itmowidgets.feature.sport.common

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.alllexey.itmowidgets.feature.sport.my.SportMyFragment
import dev.alllexey.itmowidgets.feature.sport.sign.SportSignFragment

class SportPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SportMyFragment()
            1 -> SportSignFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
