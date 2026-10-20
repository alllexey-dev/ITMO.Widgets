package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * Declarative description of a settings screen.
 *
 * Screens are data, not layouts: a new section is a list built in a ViewModel, so
 * every screen renders identically and can be asserted in a plain unit test.
 */
data class SettingSection(
    val title: UiText?,
    val items: List<SettingItem>,
    val footer: UiText? = null
)

sealed interface SettingItem {

    val id: SettingRowId

    /** The row's leading icon, in a neutral tile as on the profile; null for rows that need none (a licence). */
    val icon: AppIcon?

    data class Toggle(
        override val id: SettingRowId,
        val title: UiText,
        val description: UiText? = null,
        val checked: Boolean,
        val enabled: Boolean = true,
        override val icon: AppIcon? = null
    ) : SettingItem

    /** A single choice shown in place: a segmented control for two to four options, a list for more. */
    data class Choice(
        override val id: SettingRowId,
        val title: UiText,
        val value: UiText,
        val options: List<ChoiceOption>,
        /** Null until an asynchronous source has provided the real value. */
        val selectedOptionKey: String?,
        val description: UiText? = null,
        val enabled: Boolean = true,
        override val icon: AppIcon? = null
    ) : SettingItem

    /**
     * Opens another screen.
     *
     * [value] is only for sections that honestly collapse into a single state, such
     * as services being on or off. Sections made of independent options use a static
     * [description] of what is inside instead, because a single option would
     * misrepresent the whole section.
     */
    data class Navigation(
        override val id: SettingRowId,
        val title: UiText,
        val value: UiText? = null,
        val description: UiText? = null,
        val page: SettingsPage,
        val enabled: Boolean = true,
        override val icon: AppIcon? = null
    ) : SettingItem

    /**
     * Runs an immediate command without leaving the settings screen. [trailingIcon] says where the row leads: a
     * chevron to another screen, `open_in_new` to the browser; a command that stays on the page has none.
     */
    data class Action(
        override val id: SettingRowId,
        val title: UiText,
        val description: UiText? = null,
        val value: UiText? = null,
        val trailingIcon: AppIcon? = null,
        val enabled: Boolean = true,
        override val icon: AppIcon? = null
    ) : SettingItem

    /** Read-only fact, such as the application version. */
    data class Info(
        override val id: SettingRowId,
        val title: UiText,
        val value: UiText,
        override val icon: AppIcon? = null
    ) : SettingItem
}

data class ChoiceOption(val key: String, val label: UiText)
