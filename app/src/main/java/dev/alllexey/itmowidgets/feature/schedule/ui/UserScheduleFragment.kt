package dev.alllexey.itmowidgets.feature.schedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentUserScheduleBinding

/** Another user's schedule as a contextual screen: a titled shell around the schedule itself. */
@AndroidEntryPoint
class UserScheduleFragment : Fragment() {

    private var _binding: FragmentUserScheduleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isu = requireArguments().getInt(UserScreenArgs.ISU)
        val name = requireArguments().getString(UserScreenArgs.NAME).orEmpty()
        binding.title.text = if (name.isBlank()) {
            getString(R.string.user_profile_schedule_title)
        } else {
            getString(R.string.user_schedule_title, name.substringBefore(" "))
        }
        binding.backButton.setOnClickListener { closeScreen() }
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.schedule_container, ScheduleFragment.newInstance(isu))
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
