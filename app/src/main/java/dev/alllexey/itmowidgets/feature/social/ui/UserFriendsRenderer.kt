package dev.alllexey.itmowidgets.feature.social.ui

import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.databinding.FragmentUserFriendsBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsUiState

/** The same bounded surface shows content, empty, denied and retryable states. */
class UserFriendsRenderer(
    private val binding: FragmentUserFriendsBinding,
    private val adapter: UserListAdapter,
    private val onRetry: () -> Unit,
    private val onSettings: () -> Unit
) {
    private var generation = 0

    fun render(state: UserFriendsUiState) = with(binding) {
        val revision = ++generation
        loading.isVisible = state is UserFriendsUiState.Loading
        swipeRefreshLayout.isRefreshing = (state as? UserFriendsUiState.Content)?.refreshing == true
        when (state) {
            UserFriendsUiState.Loading -> {
                swipeRefreshLayout.isVisible = false
                stateContainer.isVisible = false
            }
            is UserFriendsUiState.Error -> {
                adapter.submitList(emptyList())
                val disabled = state.error == AppError.CustomServicesDisabled
                val hidden = state.error == AppError.Forbidden
                showState(
                    if (hidden || disabled) R.drawable.ic_lock else R.drawable.ic_error_rounded,
                    when {
                        hidden -> R.string.user_friends_hidden_title
                        disabled -> R.string.friends_disabled_title
                        else -> R.string.common_load_error_title
                    },
                    when {
                        hidden -> R.string.user_friends_hidden_description
                        disabled -> R.string.friends_disabled_description
                        else -> state.error.messageRes()
                    },
                    when {
                        hidden -> null
                        disabled -> R.string.settings_title
                        else -> R.string.common_retry
                    },
                    if (disabled) onSettings else onRetry
                )
            }
            is UserFriendsUiState.Content -> adapter.submitList(state.items) {
                if (revision != generation) return@submitList
                if (state.items.isEmpty()) {
                    showState(R.drawable.ic_group, R.string.user_friends_empty_title,
                        null, R.string.user_friends_refresh, onRetry)
                } else {
                    stateContainer.isVisible = false
                    swipeRefreshLayout.isVisible = true
                }
            }
        }
    }

    private fun showState(icon: Int, title: Int, description: Int?, action: Int?, click: () -> Unit) = with(binding) {
        swipeRefreshLayout.isVisible = false
        stateContainer.isVisible = true
        stateIcon.setImageResource(icon)
        stateTitle.setText(title)
        stateDescription.isVisible = description != null
        description?.let(stateDescription::setText)
        stateAction.isVisible = action != null
        action?.let(stateAction::setText)
        stateAction.setOnClickListener { click() }
    }
}
