package dev.alllexey.itmowidgets.ui.sport

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.alllexey.itmowidgets.ui.sport.me.SportMeFragment
import dev.alllexey.itmowidgets.ui.sport.sign.SportSignFragment

class SportPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SportMeFragment()
            1 -> SportSignFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}