package dev.alllexey.itmowidgets.feature.me.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
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
        binding.versionValue.text = BuildConfig.VERSION_NAME
        // Bind cached identity before the first frame, including activity recreation.
        render(viewModel.uiState.value)

        listOf(
            binding.friendsRow,
            binding.findPeopleRow,
            binding.privacyRow,
            binding.servicesDisabledRow,
            binding.settingsRow,
            binding.notificationsRow,
            binding.debugToolsRow
        ).forEach { ViewCompat.setScreenReaderFocusable(it, true) }

        binding.friendsRow.setOnClickListener { openScreen(AppScreen.FRIENDS) }
        binding.findPeopleRow.setOnClickListener { openScreen(AppScreen.USER_SEARCH) }
        binding.privacyRow.setOnClickListener {
            openScreen(AppScreen.SETTINGS, bundleOf(SETTINGS_PAGE_ARGUMENT to SETTINGS_PAGE_PRIVACY))
        }
        binding.servicesDisabledRow.setOnClickListener { openScreen(AppScreen.SETTINGS) }
        binding.settingsRow.setOnClickListener { openScreen(AppScreen.SETTINGS) }
        binding.notificationsRow.setOnClickListener { openNotificationSettings() }
        binding.debugToolsRow.setOnClickListener { openScreen(AppScreen.DEBUG_TOOLS) }
        binding.signOutRow.setOnClickListener { showSignOutConfirmation() }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        // Returning from a contextual screen may have changed friends or requests.
        viewModel.refresh()
        renderNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: MeUiState) {
        MeRenderer.render(binding, state)
    }

    private fun renderNotifications() {
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        binding.notificationsDescription.setText(
            if (enabled) R.string.settings_notifications_allowed else R.string.settings_notifications_blocked
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        )
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.me_sign_out_confirm_title)
            .setMessage(R.string.me_sign_out_confirm_message)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.me_sign_out) { _, _ -> viewModel.signOut() }
            .show()
    }

    private companion object {
        /** Mirrors the settings graph argument; features must not import each other. */
        const val SETTINGS_PAGE_ARGUMENT = "settings_page"
        const val SETTINGS_PAGE_PRIVACY = "PRIVACY"
    }
}
