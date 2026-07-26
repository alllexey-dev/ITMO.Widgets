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
    val items: List<SettingItem>
)

sealed interface SettingItem {

    val key: String

    data class Toggle(
        override val key: String,
        val title: UiText,
        val description: UiText? = null,
        val checked: Boolean
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
        val destinationId: Int
    ) : SettingItem

    /** Read-only fact, such as the application version. */
    data class Info(
        override val key: String,
        val title: UiText,
        val value: UiText
    ) : SettingItem
}
