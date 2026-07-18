package dev.alllexey.itmowidgets.feature.friendselector

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemFriendSelectorBinding

class FriendSelectorAdapter(
    private var selectedIsu: Int? = null,
    private val onClick: (UserData) -> Unit
) : ListAdapter<UserData, FriendSelectorAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<UserData>() {
        override fun areItemsTheSame(oldItem: UserData, newItem: UserData): Boolean {
            return oldItem.isu == newItem.isu
        }

        override fun areContentsTheSame(oldItem: UserData, newItem: UserData): Boolean {
            return oldItem == newItem
        }
    }

    fun setSelectedIsu(isu: Int?) {
        val previousIsu = selectedIsu
        selectedIsu = isu
        currentList.indexOfFirst { it.isu == previousIsu }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
        currentList.indexOfFirst { it.isu == isu }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
    }

    inner class VH(
        private val binding: ItemFriendSelectorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserData) {
            binding.name.text = item.name
            binding.subtitle.text = buildSubtitle(item)
            val canViewSchedule = item.settings.scheduleSharing
            val isSelected = item.isu == selectedIsu

            binding.root.alpha = if (canViewSchedule) 1f else 0.52f
            binding.root.isEnabled = canViewSchedule
            binding.root.isClickable = canViewSchedule

            val primary = binding.root.context.color.primary
            val onSurfaceVariant = binding.root.context.color.onSurfaceVariant
            binding.root.strokeWidth = if (isSelected) {
                binding.root.resources.getDimensionPixelSize(
                    R.dimen.friend_picker_selection_stroke
                )
            } else {
                0
            }
            binding.root.strokeColor = primary
            binding.trailingIcon.setImageResource(
                if (canViewSchedule) R.drawable.ic_check else R.drawable.ic_lock
            )
            binding.trailingIcon.imageTintList = ColorStateList.valueOf(
                if (canViewSchedule) primary else onSurfaceVariant
            )
            binding.trailingIcon.isVisible = isSelected || !canViewSchedule
            binding.sharingStatus.setText(
                if (canViewSchedule) {
                    R.string.friend_picker_schedule_open
                } else {
                    R.string.friend_picker_schedule_hidden
                }
            )
            binding.sharingStatus.setTextColor(
                if (canViewSchedule) primary else onSurfaceVariant
            )

            binding.avatar.setUser(item)
            binding.root.setOnClickListener(if (canViewSchedule) View.OnClickListener {
                onClick(item)
            } else null)
        }

        private fun buildSubtitle(item: UserData): String {
            val groupsText = when {
                item.groups.isEmpty() -> "Нет группы"
                item.groups.size == 1 -> item.groups.first().name
                item.groups.size <= 2 -> item.groups.joinToString(" • ") { it.name }
                else -> "${item.groups.first().name} • и ещё ${item.groups.size - 1}"
            }

            return "${item.isu} • $groupsText"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFriendSelectorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
