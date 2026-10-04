package dev.alllexey.itmowidgets.feature.me.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.ui.shareText
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openWebLogin
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeViewModel by viewModels()

    @Inject lateinit var shareLinks: ShareLinkFactory

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
        // Bind cached identity before the first frame, including activity recreation.
        render(viewModel.uiState.value)

        listOf(
            binding.friendsRow,
            binding.findPeopleRow,
            binding.privacyRow,
            binding.servicesDisabledRow,
            binding.webLoginRow,
            binding.settingsRow,
            binding.debugToolsRow
        ).forEach { ViewCompat.setScreenReaderFocusable(it, true) }

        binding.friendsRow.setOnClickListener { openScreen(AppScreen.FRIENDS) }
        binding.findPeopleRow.setOnClickListener { openScreen(AppScreen.USER_SEARCH) }
        binding.privacyRow.setOnClickListener {
            openScreen(AppScreen.SETTINGS, bundleOf(SETTINGS_PAGE_ARGUMENT to SETTINGS_PAGE_PRIVACY))
        }
        binding.servicesDisabledRow.setOnClickListener { openScreen(AppScreen.SETTINGS) }
        binding.webLoginRow.setOnClickListener { openWebLogin() }
        binding.settingsRow.setOnClickListener { openScreen(AppScreen.SETTINGS) }
        binding.debugToolsRow.setOnClickListener { openScreen(AppScreen.DEBUG_TOOLS) }
        binding.signOutRow.setOnClickListener { showSignOutConfirmation() }
        binding.profileShareButton.setOnClickListener { shareOwnProfile() }
        binding.githubButton.setOnClickListener { openLink(ProjectLinks.GITHUB_URL) }
        // The native client handles tg:// itself; the web page is only a fallback.
        binding.telegramButton.setOnClickListener {
            openLink(ProjectLinks.TELEGRAM_DEEPLINK, ProjectLinks.TELEGRAM_URL)
        }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        // Returning from a contextual screen may have changed friends or requests.
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: MeUiState) {
        MeRenderer.render(binding, state)
    }

    /** Shares the name and ISU the profile card shows. */
    private fun shareOwnProfile() {
        val state = viewModel.uiState.value
        val isu = (state.user?.isu ?: state.backendUser?.isu)?.takeIf { it > 0 } ?: return
        shareText(
            getString(R.string.share_profile_title),
            getString(R.string.share_profile_text, binding.profileName.text, shareLinks.profile(isu))
        )
    }

    private fun openLink(vararg urls: String) {
        for (url in urls) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                return
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        Snackbar.make(binding.root, R.string.link_open_failed, Snackbar.LENGTH_LONG).show()
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
