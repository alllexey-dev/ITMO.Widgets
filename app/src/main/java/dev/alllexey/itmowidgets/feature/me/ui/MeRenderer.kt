package dev.alllexey.itmowidgets.feature.me.ui

import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState

object MeRenderer {
    fun render(binding: FragmentMeBinding, state: MeUiState) {
        val context = binding.root.context
        val user = state.user
        val backendUser = state.backendUser
        binding.profileAvatar.setUser(
            user?.name ?: backendUser?.name,
            user?.pictureUrl ?: backendUser?.pictureUrl
        )
        binding.profileName.text = user?.name ?: backendUser?.name
            ?: context.getString(R.string.me_unknown_user)
        val group = backendUser?.groups?.firstOrNull()
        binding.profileGroup.isVisible = group != null
        binding.profileGroup.text = group?.let {
            context.getString(R.string.me_group_format, it.name, it.course, it.facultyShortName)
        }
        val isu = user?.isu ?: backendUser?.isu
        binding.profileMeta.isVisible = isu != null
        binding.profileMeta.text = isu?.let { context.getString(R.string.me_isu, it) }

        val enabled = state.friends != MeFriendsSummary.Disabled
        binding.friendsRow.isVisible = enabled
        binding.findPeopleDivider.isVisible = enabled
        binding.findPeopleRow.isVisible = enabled
        binding.privacyDivider.isVisible = enabled
        binding.privacyRow.isVisible = enabled
        binding.servicesDisabledRow.isVisible = !enabled

        val summary = state.friends as? MeFriendsSummary.Content
        binding.friendsDescription.text = when (state.friends) {
            MeFriendsSummary.Loading -> context.getString(R.string.me_friends_loading)
            MeFriendsSummary.Error -> context.getString(R.string.me_friends_error)
            is MeFriendsSummary.Content -> if (state.friends.friends == 0) {
                context.getString(R.string.me_friends_none)
            } else {
                context.getString(R.string.me_friends_count, state.friends.friends)
            }
            MeFriendsSummary.Disabled -> ""
        }
        val incoming = summary?.incomingRequests ?: 0
        binding.requestsBadge.isVisible = incoming > 0
        binding.requestsBadge.text = context.getString(R.string.me_requests_badge, incoming)
        binding.requestsBadge.contentDescription =
            context.getString(R.string.me_requests_badge_accessibility, incoming)

        binding.signOutRow.isEnabled = !state.signOutInProgress
    }
}
