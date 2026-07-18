package dev.alllexey.itmowidgets.feature.friendselector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
        selectedIsu = isu
        notifyDataSetChanged()
    }

    inner class VH(
        private val binding: ItemFriendSelectorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserData) {
            binding.name.text = item.name
            binding.subtitle.text = buildSubtitle(item)

            if (item.isu == selectedIsu) {
                binding.root.alpha = 1f
            } else {
                binding.root.alpha = 0.92f
            }

            val primary = binding.root.context.color.primary
            binding.root.strokeWidth = if (item.isu == selectedIsu) 2 else 0
            binding.root.strokeColor = primary

            binding.avatar.setUser(item)
            binding.root.setOnClickListener { onClick(item) }
        }

        private fun buildSubtitle(item: UserData): String {
            val groupsText = when {
                item.groups.isEmpty() -> "Нет группы"
                item.groups.size == 1 -> item.groups.first().name
                item.groups.size <= 2 -> item.groups.joinToString(" • ") { it.name }
                else -> "${item.groups.first().name} • и ещё ${item.groups.size - 1}"
            }

            val sharingText = if (item.settings.scheduleSharing) {
                "открыто"
            } else {
                "скрыто"
            }

            return "${item.isu} • $groupsText • $sharingText"
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
