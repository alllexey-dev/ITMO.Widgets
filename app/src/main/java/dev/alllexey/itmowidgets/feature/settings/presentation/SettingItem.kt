package dev.alllexey.itmowidgets.feature.settings.presentation

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

    val key: String

    data class Toggle(
        override val key: String,
        val title: UiText,
        val description: UiText? = null,
        val checked: Boolean,
        val enabled: Boolean = true,
        /** False while an asynchronous source has not provided the real value yet. */
        val stateKnown: Boolean = true
    ) : SettingItem

    /** Opens a single-choice Material dialog and displays the current value. */
    data class Choice(
        override val key: String,
        val title: UiText,
        val value: UiText,
        val options: List<ChoiceOption>,
        /** Null until an asynchronous source has provided the real value. */
        val selectedOptionKey: String?,
        val description: UiText? = null,
        val enabled: Boolean = true
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
        override val key: String,
        val title: UiText,
        val value: UiText? = null,
        val description: UiText? = null,
        val page: SettingsPage,
        val enabled: Boolean = true
    ) : SettingItem

    /** Runs an immediate command without leaving the settings screen. */
    data class Action(
        override val key: String,
        val title: UiText,
        val description: UiText? = null,
        val value: UiText? = null,
        val trailingIconRes: Int? = null,
        val enabled: Boolean = true
    ) : SettingItem

    /** Read-only fact, such as the application version. */
    data class Info(
        override val key: String,
        val title: UiText,
        val value: UiText
    ) : SettingItem
}

data class ChoiceOption(
    val key: String,
    val label: UiText
)
