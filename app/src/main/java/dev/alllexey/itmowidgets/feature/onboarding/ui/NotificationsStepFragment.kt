package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentOnboardingNotificationsBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The notification permission, asked where it is needed: right after the opt-in
 * that sends the pushes. One status row and one button that keeps its place.
 */
class NotificationsStepFragment : Fragment() {

    private var _binding: FragmentOnboardingNotificationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.notificationsButton.setOnClickListener { viewModel.requestNotifications() }

        viewModel.state
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: OnboardingUiState) {
        val granted = state.notificationsGranted
        binding.notificationsStatus.setText(
            if (granted) R.string.onboarding_notifications_on else R.string.onboarding_notifications_off
        )
        binding.notificationsStatus.setTextColor(
            MaterialColors.getColor(
                binding.notificationsStatus,
                if (granted) android.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface
            )
        )
        // Android will not show the dialog a second time; older Android has no dialog at all.
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.notificationsAsked
        binding.notificationsButton.setText(
            when {
                granted -> R.string.onboarding_notifications_configure
                canAsk -> R.string.onboarding_notifications_allow
                else -> R.string.onboarding_notifications_open_settings
            }
        )
    }
}
