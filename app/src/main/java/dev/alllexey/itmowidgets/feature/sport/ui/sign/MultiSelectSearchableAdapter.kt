package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.bindSelectionAccessibility
import java.util.Locale

class MultiSelectSearchableAdapter(
    private val allItems: List<SelectableItem>
) : RecyclerView.Adapter<MultiSelectSearchableAdapter.ViewHolder>() {

    private var filteredItems: MutableList<SelectableItem> = allItems.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.item_name_text_view)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.item_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_multi_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position]
        holder.textView.text = item.name
        holder.checkBox.isChecked = item.isSelected
        holder.itemView.bindSelectionAccessibility(item.name, item.isSelected, multiple = true)

        holder.itemView.setOnClickListener {
            item.isSelected = !item.isSelected
            holder.checkBox.isChecked = item.isSelected
            holder.itemView.bindSelectionAccessibility(item.name, item.isSelected, multiple = true)
        }
    }

    override fun getItemCount() = filteredItems.size

    fun filter(query: String?) {
        val lowerCaseQuery = query?.lowercase(Locale.getDefault()) ?: ""

        filteredItems = if (lowerCaseQuery.isEmpty()) {
            allItems.toMutableList()
        } else {
            allItems.filter { it.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery) }
                .toMutableList()
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<SelectableItem> {
        return allItems.filter { it.isSelected }
    }
}
