package dev.alllexey.itmowidgets.ui.sport

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentSportBinding

class SportFragment : Fragment(R.layout.fragment_sport) {

    private var _binding: FragmentSportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = binding.sportViewPager
        val tabLayout = binding.sportTabLayout

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
        binding.sportViewPager.setCurrentItem(index, true)
    }
}