package dev.alllexey.itmowidgets.feature.friendselector.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.ui.bindSelectionAccessibility
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemFriendSelectorBinding

class FriendSelectorAdapter(
    private var selectedIsu: Int? = null,
    private val onClick: (UserSummary) -> Unit
) : ListAdapter<UserSummary, FriendSelectorAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<UserSummary>() {
        override fun areItemsTheSame(oldItem: UserSummary, newItem: UserSummary): Boolean {
            return oldItem.isu == newItem.isu
        }

        override fun areContentsTheSame(oldItem: UserSummary, newItem: UserSummary): Boolean {
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

        fun bind(item: UserSummary) {
            binding.name.text = item.name
            binding.subtitle.text = buildSubtitle(item)
            val canViewSchedule = item.sharing.schedule
            val isSelected = canViewSchedule && item.isu == selectedIsu

            binding.root.alpha = if (canViewSchedule) 1f else 0.52f
            binding.root.isEnabled = canViewSchedule

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
                if (canViewSchedule) R.drawable.ic_check_rounded else R.drawable.ic_lock
            )
            binding.trailingIcon.imageTintList = ColorStateList.valueOf(
                if (canViewSchedule) primary else onSurfaceVariant
            )
            // Reserve the icon column so choosing a row never changes name wrapping.
            binding.trailingIcon.isInvisible = !isSelected && canViewSchedule
            binding.sharingStatus.setText(
                if (canViewSchedule) {
                    R.string.friend_picker_schedule_open
                } else {
                    R.string.friend_picker_schedule_hidden
                }
            )
            binding.sharingStatus.setTextColor(onSurfaceVariant)
            binding.root.bindSelectionAccessibility(
                label = listOf(item.name, binding.subtitle.text, binding.sharingStatus.text).joinToString(". "),
                selected = isSelected,
                selectable = canViewSchedule
            )

            binding.avatar.setUser(item)
            binding.root.setOnClickListener(if (canViewSchedule) View.OnClickListener {
                onClick(item)
            } else null)
            // View.setOnClickListener makes a view clickable even when the listener is null.
            binding.root.isClickable = canViewSchedule
        }

        private fun buildSubtitle(item: UserSummary): String {
            val context = binding.root.context
            val groupsText = when {
                item.groups.isEmpty() -> context.getString(R.string.friend_picker_no_group)
                item.groups.size == 1 -> item.groups.first().name
                item.groups.size <= 2 -> item.groups.joinToString(" • ") { it.name }
                else -> context.getString(
                    R.string.friend_picker_more_groups,
                    item.groups.first().name,
                    item.groups.size - 1
                )
            }

            return context.getString(
                R.string.friend_picker_user_subtitle,
                item.isu,
                groupsText
            )
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
