package dev.alllexey.itmowidgets.feature.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.card.MaterialCardView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.ItemSettingRowBinding
import dev.alllexey.itmowidgets.databinding.ItemSettingToggleBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage

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
    private val onChoice: (item: SettingItem.Choice) -> Unit,
    private val onNavigate: (page: SettingsPage) -> Unit,
    private val onAction: (key: String) -> Unit,
) {

    private val inflater = LayoutInflater.from(container.context)
    private val toggleBindings = mutableMapOf<String, ItemSettingToggleBinding>()
    private val rowBindings = mutableMapOf<String, ItemSettingRowBinding>()
    private val sectionLabels = mutableMapOf<Int, TextView>()
    private val sectionFooters = mutableMapOf<Int, TextView>()
    private var renderedStructure: List<List<String>>? = null

    fun render(sections: List<SettingSection>) {
        val structure = sections.structure()
        if (structure != renderedStructure) {
            rebuild(sections)
            renderedStructure = structure
        }
        sections.forEachIndexed { index, section ->
            sectionLabels[index]?.let { bindOptionalText(it, section.title) }
            sectionFooters[index]?.let { bindOptionalText(it, section.footer) }
            section.items.forEach(::update)
        }
    }

    private fun rebuild(sections: List<SettingSection>) {
        container.removeAllViews()
        toggleBindings.clear()
        rowBindings.clear()
        sectionLabels.clear()
        sectionFooters.clear()

        sections.forEachIndexed { index, section ->
            addSectionLabel(index)
            addSectionCard(section.items)
            addSectionFooter(index)
        }
    }

    private fun addSectionLabel(index: Int) {
        val label = inflater.inflate(
            R.layout.item_setting_group_label,
            container,
            false
        ) as TextView
        ViewCompat.setAccessibilityHeading(label, true)
        if (index == 0) {
            label.updateLayoutParams<LinearLayout.LayoutParams> {
                topMargin = (FIRST_SECTION_TOP_MARGIN_DP * container.resources.displayMetrics.density)
                    .toInt()
            }
        }
        sectionLabels[index] = label
        container.addView(label)
    }

    private fun addSectionFooter(index: Int) {
        val footer = inflater.inflate(
            R.layout.item_setting_section_footer,
            container,
            false
        ) as TextView
        sectionFooters[index] = footer
        container.addView(footer)
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
        val row = when (item) {
            is SettingItem.Toggle -> createToggle(item, parent)
            is SettingItem.Choice -> createChoice(item, parent)
            is SettingItem.Navigation -> createNavigation(item, parent)
            is SettingItem.Action -> createAction(item, parent)
            is SettingItem.Info -> createInfo(item, parent)
        }
        disableHierarchyStateSaving(row)
        ViewCompat.setScreenReaderFocusable(row, true)
        return row
    }

    private fun createToggle(item: SettingItem.Toggle, parent: ViewGroup): View {
        val binding = ItemSettingToggleBinding.inflate(inflater, parent, false)
        toggleBindings[item.key] = binding

        // MaterialSwitch animates every programmatic false -> true transition once
        // attached. Apply the first persisted value before adding the row to the
        // hierarchy so opening Settings does not replay every switch animation.
        binding.settingSwitch.isChecked = item.checked
        binding.settingSwitch.isEnabled = item.enabled && item.stateKnown
        binding.settingSwitch.isInvisible = !item.stateKnown
        binding.settingSwitch.jumpDrawablesToCurrentState()
        setRowEnabled(binding.root, item.enabled && item.stateKnown)
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun createChoice(item: SettingItem.Choice, parent: ViewGroup): View {
        val binding = ItemSettingRowBinding.inflate(inflater, parent, false)
        rowBindings[item.key] = binding

        binding.settingChevron.isVisible = true
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun createNavigation(item: SettingItem.Navigation, parent: ViewGroup): View {
        val binding = ItemSettingRowBinding.inflate(inflater, parent, false)
        rowBindings[item.key] = binding

        binding.settingChevron.isVisible = true
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
        binding.root.isClickable = true
        binding.root.setBackgroundResource(selectableItemBackground())

        return binding.root
    }

    private fun update(item: SettingItem) {
        when (item) {
            is SettingItem.Toggle -> updateToggle(item)
            is SettingItem.Choice -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                bindOptionalText(binding.settingDescription, item.description)
                bindOptionalText(binding.settingValue, item.value)
                binding.root.setOnClickListener { if (item.enabled) onChoice(item) }
                setRowEnabled(binding.root, item.enabled)
            }
            is SettingItem.Navigation -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                bindOptionalText(binding.settingDescription, item.description)
                bindOptionalText(binding.settingValue, item.value)
                binding.root.setOnClickListener { if (item.enabled) onNavigate(item.page) }
                setRowEnabled(binding.root, item.enabled)
            }
            is SettingItem.Action -> {
                val binding = rowBindings[item.key] ?: return
                binding.settingTitle.text = item.title.resolve(container.context)
                bindOptionalText(binding.settingDescription, item.description)
                bindOptionalText(binding.settingValue, item.value)
                binding.settingChevron.isVisible = item.trailingIconRes != null
                item.trailingIconRes?.let(binding.settingChevron::setImageResource)
                binding.root.setOnClickListener { if (item.enabled) onAction(item.key) }
                setRowEnabled(binding.root, item.enabled)
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
        val interactive = item.enabled && item.stateKnown

        binding.settingTitle.text = item.title.resolve(context)
        bindOptionalText(binding.settingDescription, item.description)
        binding.root.contentDescription = listOfNotNull(
            item.title.resolve(context),
            item.description?.resolve(context)
        ).joinToString(". ")
        binding.settingSwitch.isEnabled = interactive
        setRowEnabled(binding.root, interactive)

        binding.settingSwitch.setOnCheckedChangeListener(null)
        if (item.stateKnown && binding.settingSwitch.isChecked != item.checked) {
            binding.settingSwitch.isChecked = item.checked
            // Repository/DataStore emissions are state restoration, not user
            // gestures, and therefore must not animate on screen entry.
            binding.settingSwitch.jumpDrawablesToCurrentState()
        }
        binding.settingSwitch.setOnCheckedChangeListener { _, checked ->
            if (interactive) onToggle(item.key, checked)
        }
        binding.root.setOnClickListener {
            if (interactive) binding.settingSwitch.toggle()
        }
        ViewCompat.setAccessibilityDelegate(binding.root, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"
                info.isCheckable = item.stateKnown
                info.isChecked = item.stateKnown && binding.settingSwitch.isChecked
            }
        })

        // Keep the switch's space reserved while a Backend value is loading, but
        // never display a temporary false value that can later flip to true.
        binding.settingSwitch.isInvisible = !item.stateKnown
    }

    private fun setRowEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    }

    private fun disableHierarchyStateSaving(view: View) {
        // Every dynamic row uses the same resource IDs. The ViewModel is the only
        // state source; restoring a saved switch would otherwise invoke its listener.
        view.isSaveEnabled = false
        view.isSaveFromParentEnabled = false
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                disableHierarchyStateSaving(view.getChildAt(index))
            }
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

    private companion object {
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.6f
        const val FIRST_SECTION_TOP_MARGIN_DP = 8
    }

    /** Preserve section boundaries while excluding all changing text and values. */
    private fun List<SettingSection>.structure(): List<List<String>> {
        return map { section ->
            section.items.map { item -> "${item::class.simpleName}:${item.key}" }
        }
    }
}
