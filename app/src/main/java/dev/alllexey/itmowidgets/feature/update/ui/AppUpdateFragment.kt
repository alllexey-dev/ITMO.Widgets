package dev.alllexey.itmowidgets.feature.update.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentAppUpdateBinding
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateEvent
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Offers the release the backend reports as newer than this build. */
@AndroidEntryPoint
class AppUpdateFragment : Fragment() {
    @Inject lateinit var updateAction: UpdateAction
    private var _binding: FragmentAppUpdateBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppUpdateViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(viewModel.uiState.value)
        binding.updateButton.setOnClickListener { startUpdate() }
        binding.laterButton.setOnClickListener { closeScreen() }
        binding.closeButton.setOnClickListener { closeScreen() }
        binding.skipButton.setOnClickListener { viewModel.skipVersion() }
        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    AppUpdateEvent.Skipped -> closeScreen()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: AppUpdateUiState) {
        binding.updateTitle.setText(
            if (state.unsupported) R.string.app_update_unsupported_title else R.string.app_update_title
        )
        binding.updateVersions.text =
            getString(R.string.app_update_versions, state.installed, state.latest)
        val reason = getString(
            if (state.unsupported) R.string.app_update_unsupported_description else R.string.app_update_description
        )
        // Release notes come from the backend and explain the release; they do not
        // explain why this screen is open, so they follow the reason instead of replacing it.
        binding.updateDescription.text =
            if (state.note.isEmpty()) reason else "$reason\n\n${state.note}"
        // An unsupported build has nowhere to skip to: only the reminder is left.
        binding.skipButton.isVisible = !state.unsupported
    }

    private fun startUpdate() {
        updateAction.start(requireActivity(), viewModel.uiState.value.unsupported) {
            _binding?.let { Snackbar.make(it.root, R.string.app_update_open_failed, Snackbar.LENGTH_LONG).show() }
        }
    }
}
