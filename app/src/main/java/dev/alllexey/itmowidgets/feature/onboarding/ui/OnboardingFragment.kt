package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.databinding.FragmentOnboardingBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Host of the first-run flow: the step dots, the pager and the footer.
 *
 * Every event of the shared ViewModel is handled here. The steps are pages of one
 * `Channel`; a second collector inside a page would take events away from this one.
 */
@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()
    private lateinit var pinRequester: WidgetPinRequester
    private lateinit var adapter: OnboardingStepAdapter
    private var backCallback: OnBackPressedCallback? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinRequester = WidgetPinRequester(requireContext())
        // The launcher confirms the pin while this screen is stopped, so the receiver
        // lives as long as the Fragment, not as long as its view.
        pinRequester.start { provider ->
            WidgetKind.entries.firstOrNull { it.providerClassName == provider }?.let(viewModel::onWidgetPinned)
        }
        viewModel.onPinSupportChanged(pinRequester.isSupported)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OnboardingStepAdapter(this)
        binding.onboardingPager.apply {
            adapter = this@OnboardingFragment.adapter
            // The flow moves through its footer, never by a swipe past an unanswered step.
            isUserInputEnabled = false
        }

        val initialFooterPadding = binding.onboardingFooter.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingFooter) { footer, insets ->
            footer.updatePadding(
                bottom = initialFooterPadding +
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.onboardingFooter)

        binding.skipButton.setOnClickListener { viewModel.skip() }
        binding.nextButton.setOnClickListener { viewModel.next() }

        // Back leaves the flow only from its first step; further in it is a step back.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = viewModel.back()
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }

        viewModel.state
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        // Covers both the runtime permission and the system switch on older Android.
        viewModel.onNotificationPermission(
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backCallback = null
        binding.onboardingPager.adapter = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        pinRequester.stop()
    }

    private fun render(state: OnboardingUiState) {
        adapter.submit(state.steps)
        binding.onboardingSteps.render(state.steps.size, state.stepIndex)
        if (binding.onboardingPager.currentItem != state.stepIndex) {
            binding.onboardingPager.setCurrentItem(state.stepIndex, true)
        }
        binding.skipButton.isVisible = !state.isLastStep
        binding.nextButton.setText(if (state.isLastStep) R.string.onboarding_done else R.string.onboarding_next)
        // The activity leaves the flow on its own; until it does, one tap is enough.
        binding.skipButton.isEnabled = !state.finished
        binding.nextButton.isEnabled = !state.finished
        backCallback?.isEnabled = state.stepIndex > 0 && !state.finished
    }

    private fun handle(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.RequestPinWidget -> pinRequester.request(event.kind.providerClassName)
            OnboardingEvent.RequestNotificationPermission -> requestNotificationPermission()
            OnboardingEvent.OpenNotificationSettings -> openNotificationSettings()
            OnboardingEvent.SpoilerImageFailed -> Snackbar.make(
                binding.root,
                R.string.settings_qr_custom_image_failed,
                Snackbar.LENGTH_LONG
            ).show()
            is OnboardingEvent.ShowError -> Snackbar.make(
                binding.root,
                event.error.messageRes(),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openNotificationSettings()
            return
        }
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        )
    }
}

private val WidgetKind.providerClassName: String
    get() = when (this) {
        WidgetKind.SINGLE_LESSON -> WidgetProviders.SINGLE_LESSON
        WidgetKind.DAY_SCHEDULE -> WidgetProviders.DAY_SCHEDULE
        WidgetKind.QR -> WidgetProviders.QR_CODE
    }
