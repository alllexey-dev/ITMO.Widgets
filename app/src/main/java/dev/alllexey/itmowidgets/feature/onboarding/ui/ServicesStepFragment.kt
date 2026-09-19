package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentOnboardingServicesBinding
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The backend opt-in as one switch row, with the stored data named in full.
 *
 * The list of stored fields is the user-facing copy of the Backend schema: a new
 * table with user data adds a line to `fragment_onboarding_services.xml`.
 */
class ServicesStepFragment : Fragment() {

    private var _binding: FragmentOnboardingServicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.servicesRow.setOnClickListener {
            viewModel.setServicesEnabled(!binding.servicesSwitch.isChecked)
        }
        binding.servicesSourceButton.setOnClickListener { openSourceCode() }

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
        // The switch and the spinner share one slot, so the row never changes height.
        binding.servicesSwitch.isChecked = state.servicesEnabled
        binding.servicesSwitch.isInvisible = state.servicesBusy
        binding.servicesProgress.isVisible = state.servicesBusy
        binding.servicesRow.isEnabled = !state.servicesBusy
    }

    private fun openSourceCode() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, getString(R.string.onboarding_services_source_url).toUri())
            )
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.link_open_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}
