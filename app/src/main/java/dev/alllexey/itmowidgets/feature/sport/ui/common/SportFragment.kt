package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentSportBinding

@AndroidEntryPoint
class SportFragment : Fragment() {

    // region Binding

    private var _binding: FragmentSportBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSportBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = binding.sportViewPager
        val tabLayout = binding.sportTabLayout

        viewPager.adapter = SportPagerAdapter(this)
        viewPager.isUserInputEnabled = true
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_sport_my)
                1 -> getString(R.string.title_sport_sign)
                else -> null
            }
        }.attach()
    }

    fun changeView(index: Int) {
        binding.sportViewPager.setCurrentItem(index, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // endregion

}
