package dev.alllexey.itmowidgets.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R

/**
 * One optional skeleton row for a `ConcatAdapter`, so a list whose header is
 * itself an adapter can show its first-load placeholder under that header.
 */
class SkeletonListAdapter : RecyclerView.Adapter<SkeletonListAdapter.Holder>() {

    private var visible = false

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) notifyItemInserted(0) else notifyItemRemoved(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_skeleton_cards, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    override fun getItemCount(): Int = if (visible) 1 else 0

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view)
}
