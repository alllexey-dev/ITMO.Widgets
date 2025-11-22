package dev.alllexey.itmowidgets.ui.sport

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.alllexey.itmowidgets.R
import kotlinx.coroutines.launch

class SportFragment : Fragment(R.layout.fragment_sport) {

    private lateinit var viewPager: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.sport_view_pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.sport_tab_layout)

        viewPager.adapter = SportPagerAdapter(this)
        viewPager.isUserInputEnabled = false
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_sport_me)
                1 -> getString(R.string.title_sport_sign)
                else -> null
            }
        }.attach()
    }

    fun changeView(index: Int) {
        viewPager.setCurrentItem(index, true)
    }
}