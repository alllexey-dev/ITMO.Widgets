package dev.alllexey.itmowidgets.feature.update.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentAppUpdateBinding
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Offers the release the backend reports as newer than this build. */
@AndroidEntryPoint
class AppUpdateFragment : Fragment() {
    private var _binding: FragmentAppUpdateBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppUpdateViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(viewModel.uiState)
        binding.updateButton.setOnClickListener { openLatestRelease() }
        binding.laterButton.setOnClickListener { closeScreen() }
        binding.closeButton.setOnClickListener { closeScreen() }
        binding.skipButton.setOnClickListener { viewModel.skipVersion() }
        viewModel.skipped
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { closeScreen() }
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

    private fun openLatestRelease() {
        val release = Intent(Intent.ACTION_VIEW, getString(R.string.latest_release_url).toUri())
        runCatching { startActivity(release) }.onFailure {
            Snackbar.make(binding.root, R.string.app_update_open_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}
