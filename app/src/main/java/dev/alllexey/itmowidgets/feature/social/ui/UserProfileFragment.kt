package dev.alllexey.itmowidgets.feature.social.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.FragmentUserProfileBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.alllexey.itmowidgets.core.ui.userDisplayName

@AndroidEntryPoint
class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

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
        binding.primaryAction.setOnClickListener { viewModel.onPrimaryAction() }
        binding.secondaryAction.setOnClickListener { viewModel.onSecondaryAction() }
        binding.stateAction.setOnClickListener { viewModel.load() }

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
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: UserProfileUiState) = with(binding) {
        loading.isVisible = state is UserProfileUiState.Loading
        content.isVisible = state is UserProfileUiState.Content
        stateContainer.isVisible = state is UserProfileUiState.Error
        when (state) {
            UserProfileUiState.Loading -> Unit
            is UserProfileUiState.Error -> renderError(state.error)
            is UserProfileUiState.Content -> renderContent(state)
        }
    }

    private fun renderError(error: AppError) = with(binding) {
        val notFound = error == AppError.NotFound
        stateIcon.setImageResource(if (notFound) R.drawable.ic_person else R.drawable.ic_error_rounded)
        stateTitle.setText(if (notFound) R.string.user_profile_not_found_title else R.string.common_load_error_title)
        stateDescription.text = if (notFound) {
            getString(R.string.user_profile_not_found_description)
        } else {
            getString(error.messageRes())
        }
        stateAction.isVisible = !notFound
    }

    private fun renderContent(state: UserProfileUiState.Content) = with(binding) {
        val user = state.profile.user
        avatar.setUser(user)
        name.text = requireContext().userDisplayName(user.name, user.isu)
        val firstGroup = user.primaryGroup()
        group.isVisible = firstGroup != null
        group.text = firstGroup?.let {
            getString(R.string.me_group_format, it.name, it.course, it.facultyShortName)
        }
        meta.text = getString(R.string.me_isu, user.isu)

        val relationship = state.profile.relationship
        relationshipStatus.isVisible = state.isSelf || relationship == RelationshipState.INCOMING
        relationshipStatus.text = when {
            state.isSelf -> getString(R.string.user_profile_self)
            else -> getString(R.string.user_status_incoming)
        }
        actions.isVisible = !state.isSelf && relationship != RelationshipState.BLOCKED
        primaryAction.isEnabled = !state.busy
        secondaryAction.isEnabled = !state.busy
        secondaryAction.isVisible = relationship == RelationshipState.INCOMING
        secondaryAction.setText(R.string.user_profile_reject_request)
        when (relationship) {
            RelationshipState.NONE -> primaryAction.applyStyle(
                R.string.user_profile_add_friend, filled = true
            )
            RelationshipState.OUTGOING -> primaryAction.applyStyle(
                R.string.user_profile_cancel_request, filled = false
            )
            RelationshipState.INCOMING -> primaryAction.applyStyle(
                R.string.user_profile_accept_request, filled = true
            )
            RelationshipState.FRIENDS -> primaryAction.applyStyle(
                R.string.user_profile_remove_friend, filled = false
            )
            RelationshipState.BLOCKED -> Unit
        }

        bindEntry(friendsRow, friendsDescription, friendsTrailing, user.sharing.friends, R.string.user_profile_friends_open) {
            openScreen(AppScreen.USER_FRIENDS, userArguments(user.isu, user.name))
        }
        val scheduleOpen = state.isSelf || user.sharing.schedule
        val sportOpen = state.isSelf || user.sharing.sport
        bindEntry(scheduleRow, scheduleDescription, scheduleTrailing, scheduleOpen, R.string.user_profile_schedule_open) {
            openScreen(AppScreen.USER_SCHEDULE, userArguments(user.isu, user.name))
        }
        bindEntry(sportRow, sportDescription, sportTrailing, sportOpen, R.string.user_profile_sport_open) {
            openScreen(AppScreen.USER_SPORT, userArguments(user.isu, user.name))
        }
        hiddenHint.isVisible = !state.isSelf && relationship != RelationshipState.FRIENDS &&
            (!scheduleOpen || !sportOpen)
    }

    private fun bindEntry(
        row: View,
        description: TextView,
        trailing: ImageView,
        open: Boolean,
        openDescription: Int,
        onOpen: () -> Unit
    ) {
        description.setText(if (open) openDescription else R.string.user_profile_hidden)
        trailing.setImageResource(if (open) R.drawable.ic_chevron_right else R.drawable.ic_lock)
        row.isClickable = open
        row.setOnClickListener(if (open) { _ -> onOpen() } else null)
        row.alpha = if (open) 1f else 0.72f
    }

    /** One button, two weights: filled for joining, tonal for stepping back. */
    private fun MaterialButton.applyStyle(text: Int, filled: Boolean) {
        setText(text)
        val colors = context.color
        backgroundTintList = ColorStateList.valueOf(if (filled) colors.primary else colors.secondaryContainer)
        setTextColor(if (filled) colors.onPrimary else colors.onSecondaryContainer)
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
        }
    }

    private fun userArguments(isu: Int, name: String) = bundleOf(
        UserScreenArgs.ISU to isu,
        UserScreenArgs.NAME to name
    )
}
