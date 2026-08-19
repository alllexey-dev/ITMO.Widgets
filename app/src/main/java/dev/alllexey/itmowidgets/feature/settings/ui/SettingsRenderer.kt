package dev.alllexey.itmowidgets.feature.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.ItemSettingRowBinding
import dev.alllexey.itmowidgets.databinding.ItemSettingToggleBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection

/**
 * Inflates a declarative settings screen into [container].
 *
 * Settings screens are short and fully known up front, so a plain scrolling column
 * is enough — a RecyclerView would add diffing and view types for no benefit.
 *
 * Views are rebuilt only when the set of rows changes. Re-inflating on every state
 * emission would destroy the switch the user just tapped and swallow its animation.
 */
class SettingsRenderer(
    private val container: LinearLayout,
    private val onToggle: (key: String, checked: Boolean) -> Unit,
    private val onNavigate: (destinationId: Int) -> Unit,
    private val onAction: (key: String) -> Unit,
) {

    private val inflater = LayoutInflater.from(container.context)
    private val toggleBindings = mutableMapOf<String, ItemSettingToggleBinding>()
    private val rowBindings = mutableMapOf<String, ItemSettingRowBinding>()
    private var renderedStructure: List<String>? = null

    fun render(sections: List<SettingSection>) {
        val structure = sections.structure()
        if (structure != renderedStructure) {
            rebuild(sections)
            renderedStructure = structure
        }
        sections.flatMap(SettingSection::items).forEach(::update)
    }

    private fun rebuild(sections: List<SettingSection>) {
        container.removeAllViews()
        toggleBindings.clear()
        rowBindings.clear()

        sections.forEach { section ->
            section.title?.let(::addSectionLabel)
            addSectionCard(section.items)
        }
    }

    private fun addSectionLabel(title: UiText) {
        val label = inflater.inflate(
            R.layout.item_setting_group_label,
            container,
            false
        ) as TextView
        label.text = title.resolve(container.context)
        container.addView(label)
    }

    private fun addSectionCard(items: List<SettingItem>) {
        val card = inflater.inflate(R.layout.item_setting_card, container, false)
            as MaterialCardView
        val rows = card.findViewById<LinearLayout>(R.id.section_rows)

        items.forEachIndexed { index, item ->
            if (index > 0) rows.addView(createDivider(rows))
            rows.addView(createRow(item, rows))
        }

        container.addView(card)
    }

    private fun createRow(item: SettingItem, parent: ViewGroup): View {
        return when (item) {
            is SettingItem.Toggle -> createToggle(item, parent)
            is SettingItem.Navigation -> createNavigation(item, parent)
            is SettingItem.Action -> createAction(item, parent)
            is SettingItem.Info -> createInfo(item, parent)
        }
    }

    private fun createToggle(item: SettingItem.Toggle, parent: ViewGroup): View {
        val binding = ItemSettingToggleBinding.inflate(inflater, parent, false)
        toggleBindings[item.key] = binding

        binding.settingSwitch.setOnCheckedChangeListener { _, checked ->
            onToggle(item.key, checked)
        }
        binding.root.setOnClickListener { binding.settingSwitch.toggle() }
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun createNavigation(item: SettingItem.Navigation, parent: ViewGroup): View {
        val binding = ItemSettingRowBinding.inflate(inflater, parent, false)
        rowBindings[item.key] = binding

        binding.settingChevron.isVisible = true
        binding.root.setOnClickListener { onNavigate(item.destinationId) }
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun createInfo(item: SettingItem.Info, parent: ViewGroup): View {
        val binding = ItemSettingRowBinding.inflate(inflater, parent, false)
        rowBindings[item.key] = binding

        binding.settingChevron.isVisible = false

        return binding.root
    }

    private fun createAction(item: SettingItem.Action, parent: ViewGroup): View {
        val binding = ItemSettingRowBinding.inflate(inflater, parent, false)
        rowBindings[item.key] = binding

        binding.settingChevron.isVisible = item.trailingIconRes != null
        item.trailingIconRes?.let(binding.settingChevron::setImageResource)
        binding.root.setOnClickListener { onAction(item.key) }
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun update(item: SettingItem) {
        when (item) {
            is SettingItem.Toggle -> updateToggle(item)
            is SettingItem.Navigation -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                bindOptionalText(binding.settingDescription, item.description)
                bindOptionalText(binding.settingValue, item.value)
            }
            is SettingItem.Action -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                bindOptionalText(binding.settingDescription, item.description)
                binding.settingValue.isVisible = false
            }
            is SettingItem.Info -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                binding.settingDescription.isVisible = false
                bindOptionalText(binding.settingValue, item.value)
            }
        }
    }

    private fun updateToggle(item: SettingItem.Toggle) {
        val binding = toggleBindings[item.key] ?: return
        val context = container.context

        binding.settingTitle.text = item.title.resolve(context)
        bindOptionalText(binding.settingDescription, item.description)
        binding.settingSwitch.contentDescription = item.title.resolve(context)

        // Assigning the same value would cancel the animation already running from
        // the user's tap, so only a genuine external change is applied.
        if (binding.settingSwitch.isChecked == item.checked) return

        val listener = binding.settingSwitch.let { switch ->
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = item.checked
            switch
        }
        listener.setOnCheckedChangeListener { _, checked ->
            onToggle(item.key, checked)
        }
    }

    private fun bindOptionalText(view: TextView, text: UiText?) {
        view.isVisible = text != null
        view.text = text?.resolve(container.context)
    }

    private fun createDivider(parent: ViewGroup): View {
        return inflater.inflate(R.layout.item_setting_divider, parent, false)
    }

    private fun selectableItemBackground(): Int {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typed = container.context.obtainStyledAttributes(attrs)
        val resource = typed.getResourceId(0, 0)
        typed.recycle()
        return resource
    }

    /** Identity of the rendered rows; values are excluded on purpose. */
    private fun List<SettingSection>.structure(): List<String> {
        return flatMap { section ->
            listOfNotNull(section.title?.let { "section:${it.hashCode()}" }) +
                section.items.map { item -> "${item::class.simpleName}:${item.key}" }
        }
    }
}
