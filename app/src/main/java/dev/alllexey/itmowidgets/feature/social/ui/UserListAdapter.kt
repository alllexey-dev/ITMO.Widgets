package dev.alllexey.itmowidgets.feature.social.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.ItemUserListHeaderBinding
import dev.alllexey.itmowidgets.databinding.ItemUserListLoadMoreBinding
import dev.alllexey.itmowidgets.databinding.ItemUserRowBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi

/** Renders people lists shared by friends, requests and search: one row layout, actions by state. */
class UserListAdapter(
    private val onAction: (UserRowUi, UserAction) -> Unit,
    private val onOpen: (UserRowUi) -> Unit,
    private val onLoadMore: () -> Unit = {}
) : ListAdapter<UserListItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is UserListItem.Header -> TYPE_HEADER
        is UserListItem.User -> TYPE_USER
        UserListItem.LoadMore -> TYPE_LOAD_MORE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemUserListHeaderBinding.inflate(inflater, parent, false))
            TYPE_USER -> UserHolder(ItemUserRowBinding.inflate(inflater, parent, false))
            else -> LoadMoreHolder(ItemUserListLoadMoreBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is UserListItem.Header -> (holder as HeaderHolder).bind(item)
            is UserListItem.User -> (holder as UserHolder).bind(item.row)
            UserListItem.LoadMore -> Unit
        }
    }

    private class HeaderHolder(
        private val binding: ItemUserListHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserListItem.Header) {
            binding.headerTitle.text = item.title.resolve(binding.root.context)
        }
    }

    private inner class LoadMoreHolder(
        binding: ItemUserListLoadMoreBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.loadMoreButton.setOnClickListener { onLoadMore() }
        }
    }

    private inner class UserHolder(
        private val binding: ItemUserRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: UserRowUi) = with(binding) {
            val context = root.context
            avatar.setUser(row.name, row.pictureUrl)
            name.text = row.name
            subtitle.text = row.subtitle.resolve(context)
            status.isVisible = row.status != null
            status.text = row.status?.resolve(context)
            bindAction(primaryAction, row, row.primary)
            bindAction(secondaryAction, row, row.secondary)
            trailingIcon.isVisible = row.opensProfile && row.primary == null && row.secondary == null
            root.isClickable = row.opensProfile
            root.setOnClickListener(if (row.opensProfile) { _ -> onOpen(row) } else null)
            root.contentDescription = listOfNotNull(
                row.name,
                subtitle.text,
                status.text.takeIf { row.status != null }
            ).joinToString(". ")
        }

        private fun bindAction(
            button: com.google.android.material.button.MaterialButton,
            row: UserRowUi,
            action: UserAction?
        ) {
            button.isVisible = action != null
            if (action == null) return
            button.setText(action.labelRes())
            button.isEnabled = !row.busy
            button.setOnClickListener { onAction(row, action) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<UserListItem>() {
        override fun areItemsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean = when {
            oldItem is UserListItem.User && newItem is UserListItem.User -> oldItem.row.isu == newItem.row.isu
            oldItem is UserListItem.Header && newItem is UserListItem.Header -> oldItem.title == newItem.title
            else -> oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_USER = 1
        const val TYPE_LOAD_MORE = 2
    }
}

fun UserAction.labelRes(): Int = when (this) {
    UserAction.ADD -> R.string.user_action_add
    UserAction.ACCEPT -> R.string.user_action_accept
    UserAction.REJECT -> R.string.user_action_reject
    UserAction.CANCEL -> R.string.user_action_cancel
    UserAction.REMOVE -> R.string.user_action_remove
    UserAction.INVITE -> R.string.user_action_invite
}
