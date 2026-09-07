package dev.alllexey.itmowidgets.feature.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.SettingsLevelMotion
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reenterTransition = SettingsLevelMotion.transition(forward = false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.debugToolsRow.isVisible = BuildConfig.DEBUG
        binding.debugDivider.isVisible = BuildConfig.DEBUG
        // The view is recreated on back navigation; render cached identity before motion starts.
        render(viewModel.uiState.value)

        listOf(binding.settingsRow, binding.debugToolsRow).forEach {
            ViewCompat.setScreenReaderFocusable(it, true)
        }
        binding.settingsRow.setOnClickListener {
            exitTransition = SettingsLevelMotion.transition(forward = true)
            findNavController().navigate(R.id.action_me_to_settings)
        }
        binding.debugToolsRow.setOnClickListener {
            findNavController().navigate(R.id.action_me_to_debug_tools)
        }
        binding.signOutRow.setOnClickListener {
            showSignOutConfirmation()
        }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        // Settings motion is opt-in per navigation, not an animation for bottom-tab changes.
        exitTransition = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: MeUiState) {
        MeRenderer.render(binding, state)
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.me_sign_out_confirm_title)
            .setMessage(R.string.me_sign_out_confirm_message)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.me_sign_out) { _, _ -> viewModel.signOut() }
            .show()
    }
}
