package dev.alllexey.itmowidgets.feature.friendselector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.ui.bindSelectionAccessibility
import dev.alllexey.itmowidgets.databinding.ItemRecentFriendBinding

sealed interface RecentFriendItem {
    data object MySchedule : RecentFriendItem
    data class Friend(val user: UserSummary) : RecentFriendItem
}

class RecentFriendAdapter(
    private val onClick: (RecentFriendItem) -> Unit
) : RecyclerView.Adapter<RecentFriendAdapter.ViewHolder>() {

    private var items: List<RecentFriendItem> = listOf(RecentFriendItem.MySchedule)
    private var selectedIsu: Int? = null
    private var currentUser: UserSummary? = null

    fun submitItems(
        recentFriends: List<UserSummary>,
        selectedIsu: Int?,
        currentUser: UserSummary?
    ) {
        items = listOf(RecentFriendItem.MySchedule) +
            recentFriends.filter { it.sharing.schedule }.map(RecentFriendItem::Friend)
        this.selectedIsu = selectedIsu
        this.currentUser = currentUser
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemRecentFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentFriendItem) {
            val friend = (item as? RecentFriendItem.Friend)?.user
            val isSelected = when (item) {
                RecentFriendItem.MySchedule -> selectedIsu == null
                is RecentFriendItem.Friend -> selectedIsu == item.user.isu
            }

            // Own entry falls back to the generic icon until the profile is known.
            val avatarUser = when (item) {
                RecentFriendItem.MySchedule -> currentUser
                is RecentFriendItem.Friend -> item.user
            }

            binding.avatar.isVisible = avatarUser != null
            binding.myScheduleIcon.isVisible = avatarUser == null
            binding.avatar.setUser(avatarUser)
            binding.name.text = friend?.name?.substringBefore(" ")
                ?: binding.root.context.getString(R.string.friend_picker_my_schedule_short)
            binding.selectionIndicator.isVisible = isSelected
            binding.avatarContainer.strokeWidth = if (isSelected) {
                binding.root.resources.getDimensionPixelSize(
                    R.dimen.friend_picker_selection_stroke
                )
            } else {
                0
            }
            binding.root.bindSelectionAccessibility(
                label = friend?.name ?: binding.root.context.getString(R.string.friend_picker_my_schedule_accessibility),
                selected = isSelected
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
