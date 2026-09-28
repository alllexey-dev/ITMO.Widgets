package dev.alllexey.itmowidgets.feature.social.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.core.ui.userDisplayName
import dev.alllexey.itmowidgets.databinding.FragmentUserProfileBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserProfileAdapter
    private var renderRevision = 0L

    private val viewModel: UserProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { closeScreen() }
        binding.stateAction.setOnClickListener { viewModel.retry() }
        adapter = UserProfileAdapter(ProfileActions(
            onPrimary = viewModel::onPrimaryAction,
            onSecondary = viewModel::onSecondaryAction,
            onFriends = { openUserScreen(AppScreen.USER_FRIENDS) },
            onSchedule = { openUserScreen(AppScreen.USER_SCHEDULE) },
            onSport = { openUserScreen(AppScreen.USER_SPORT) },
            onSource = { openLink(it, binding.root) }
        ))
        binding.profileList.adapter = adapter
        binding.profileList.itemAnimator = null
        binding.profileList.addItemDecoration(ProfileItemSpacing())

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.eventFlow
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.profileList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: UserProfileUiState) {
        val revision = ++renderRevision
        when (state) {
            UserProfileUiState.Loading -> with(binding) {
                loading.isVisible = true
                profileList.isVisible = false
                stateContainer.isVisible = false
            }
            is UserProfileUiState.Error -> with(binding) {
                loading.isVisible = false
                profileList.isVisible = false
                stateContainer.isVisible = true
                renderError(state.error)
            }
            is UserProfileUiState.Content -> {
                val currentBinding = binding
                adapter.submitContent(state) {
                    // A pending diff must not reveal an old page after an error or a new view.
                    if (_binding !== currentBinding || renderRevision != revision) return@submitContent
                    currentBinding.profileList.invalidateItemDecorations()
                    currentBinding.loading.isVisible = false
                    currentBinding.stateContainer.isVisible = false
                    currentBinding.profileList.isVisible = true
                }
            }
        }
    }

    private fun renderError(error: AppError) = with(binding) {
        val notFound = error == AppError.NotFound
        stateIcon.setImageResource(if (notFound) R.drawable.ic_person else R.drawable.ic_error_rounded)
        stateTitle.setText(if (notFound) R.string.user_profile_not_found_title else R.string.common_load_error_title)
        stateDescription.isVisible = !notFound
        stateDescription.text = if (notFound) null else getString(error.messageRes())
        stateAction.isVisible = !notFound
    }

    private fun openUserScreen(screen: AppScreen) {
        val content = viewModel.uiState.value as? UserProfileUiState.Content ?: return
        openScreen(screen, bundleOf(
            UserScreenArgs.ISU to content.isu,
            UserScreenArgs.NAME to requireContext().userDisplayName(content.name, content.isu)
        ))
    }

    private fun handle(event: UserProfileEvent) {
        when (event) {
            is UserProfileEvent.ActionFailed -> Snackbar.make(
                binding.root,
                getString(R.string.friends_action_failed, getString(event.error.messageRes())),
                Snackbar.LENGTH_LONG
            ).show()
            is UserProfileEvent.ConfirmRemove -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.friends_remove_confirm_title)
                .setMessage(getString(R.string.friends_remove_confirm_message, requireContext().userDisplayName(event.name, viewModel.isu)))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.user_action_remove) { _, _ -> viewModel.removeFriend() }
                .show()
            UserProfileEvent.LoadFailed -> Snackbar.make(
                binding.root,
                R.string.common_partial_load_error,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.common_retry) { viewModel.retry() }.show()
        }
    }
}
