package dev.alllexey.itmowidgets.feature.debug.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.DebugOnly
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsEvent
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalDate

/**
 * The debug tools destination (`@id/debug_tools`), kept by name for the overlay graph. A release build gets an empty
 * view and never creates the ViewModel; a debug build shows [DebugToolsScreen]. Toasts and recreating the activity
 * after an override stay here.
 */
@AndroidEntryPoint
@DebugOnly
class DebugToolsFragment : Fragment() {

    private val viewModel: DebugToolsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (!BuildConfig.DEBUG) return View(inflater.context)
        return itmoComposeView {
            val state by viewModel.uiState.collectAsState()
            DebugToolsScreen(state, actions)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!BuildConfig.DEBUG) return
        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun handle(event: DebugToolsEvent) {
        when (event) {
            DebugToolsEvent.RecreateActivity -> requireActivity().recreate()
            DebugToolsEvent.RefreshTokenUpdated -> {
                Toast.makeText(requireContext(), R.string.debug_refresh_token_updated, Toast.LENGTH_SHORT).show()
                requireActivity().recreate()
            }
            is DebugToolsEvent.RefreshTokenUpdateFailed -> Toast.makeText(
                requireContext(),
                getString(R.string.debug_refresh_token_failed, getString(event.error.messageRes())),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val actions = object : DebugToolsActions {
        override fun back() = closeScreen()
        override fun replaceRefreshToken(refreshToken: String) = viewModel.replaceRefreshToken(refreshToken)
        override fun setDateOverride(date: LocalDate?) = viewModel.setDateOverride(date)
        override fun setScoreOverride(attendances: Int, bonus: Int) = viewModel.setScoreOverride(attendances, bonus)
        override fun clearScoreOverride() = viewModel.clearScoreOverride()
        override fun setLessonTemplatesEnabled(enabled: Boolean) = viewModel.setLessonTemplatesEnabled(enabled)
        override fun setCustomServicesEnabled(enabled: Boolean) = viewModel.setCustomServicesEnabled(enabled)
        override fun checkScheduleChanges() = viewModel.checkScheduleChanges()
        override fun checkMarks() = viewModel.checkMarks()
        override fun probeBarsSession() = viewModel.probeBarsSession()
    }
}
