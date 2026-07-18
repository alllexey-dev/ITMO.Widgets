package dev.alllexey.itmowidgets.feature.sport.sign

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.databinding.ItemContentStateBinding

data class ContentState(
    @param:DrawableRes val iconRes: Int,
    val title: String,
    val description: String,
    val action: String? = null
)

class ContentStateAdapter(
    private val onAction: () -> Unit
) : RecyclerView.Adapter<ContentStateAdapter.StateViewHolder>() {

    private var state: ContentState? = null

    fun submitState(newState: ContentState?) {
        if (state == newState) return
        state = newState
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StateViewHolder {
        val binding = ItemContentStateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StateViewHolder, position: Int) {
        holder.bind(state)
    }

    override fun getItemCount(): Int = 1

    inner class StateViewHolder(
        private val binding: ItemContentStateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: ContentState?) {
            binding.root.isVisible = state != null
            if (state == null) return

            binding.stateIcon.setImageResource(state.iconRes)
            binding.stateTitle.text = state.title
            binding.stateDescription.text = state.description
            binding.stateAction.isVisible = state.action != null
            binding.stateAction.text = state.action
            binding.stateAction.setOnClickListener { onAction() }
        }
    }
}
