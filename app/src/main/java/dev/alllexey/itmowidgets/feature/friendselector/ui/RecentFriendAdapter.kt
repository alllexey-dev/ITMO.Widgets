package dev.alllexey.itmowidgets.feature.friendselector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
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

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT
    }

    fun submitItems(
        recentFriends: List<UserSummary>,
        selectedIsu: Int?,
        currentUser: UserSummary?
    ) {
        val previous = items
        val updated = listOf(RecentFriendItem.MySchedule) +
            recentFriends.filter { it.sharing.schedule }.distinctBy(UserSummary::isu).map(RecentFriendItem::Friend)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = updated.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                id(previous[oldItemPosition]) == id(updated[newItemPosition])
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition] == updated[newItemPosition]
        })
        items = updated
        diff.dispatchUpdatesTo(this)
        setSelectedIsu(selectedIsu)
        setCurrentUser(currentUser)
        if (stateRestorationPolicy != StateRestorationPolicy.ALLOW) {
            stateRestorationPolicy = StateRestorationPolicy.ALLOW
        }
    }

    fun setSelectedIsu(isu: Int?) {
        if (selectedIsu == isu) return
        val previous = selectedIsu
        selectedIsu = isu
        listOf(previous, isu).forEach { selected ->
            val itemId = selected?.toLong() ?: MY_SCHEDULE_ID
            items.indexOfFirst { id(it) == itemId }.takeIf { it >= 0 }
                ?.let { notifyItemChanged(it, SELECTION_PAYLOAD) }
        }
    }

    fun setCurrentUser(user: UserSummary?) {
        if (currentUser == user) return
        currentUser = user
        notifyItemChanged(0)
    }

    override fun getItemId(position: Int): Long = id(items[position])

    private fun id(item: RecentFriendItem): Long = when (item) {
        RecentFriendItem.MySchedule -> MY_SCHEDULE_ID
        is RecentFriendItem.Friend -> item.user.isu.toLong()
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == SELECTION_PAYLOAD }) holder.bindSelection(items[position])
        else holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemRecentFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentFriendItem) {
            val friend = (item as? RecentFriendItem.Friend)?.user
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
            bindSelection(item)
            binding.root.setOnClickListener { onClick(item) }
        }

        fun bindSelection(item: RecentFriendItem) {
            val friend = (item as? RecentFriendItem.Friend)?.user
            val isSelected = when (item) {
                RecentFriendItem.MySchedule -> selectedIsu == null
                is RecentFriendItem.Friend -> selectedIsu == item.user.isu
            }
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
        }
    }

    private companion object {
        const val MY_SCHEDULE_ID = Long.MIN_VALUE
        const val SELECTION_PAYLOAD = "selection"
    }
}
