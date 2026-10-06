package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_friends_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_marks_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_schedule_changes_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_schedule_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_sport_title
import org.jetbrains.compose.resources.StringResource

/** One switch per home feed card. */
class HomePageProvider(
    private val repository: SettingsRepository
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.HOME)

    override val rows = HOME_CARDS.map { it.id }.toSet()

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = null,
            items = HOME_CARDS.map { card ->
                SettingItem.Toggle(
                    id = card.id,
                    title = UiText.Res(card.title),
                    checked = card.kind !in state.local.hiddenHomeCards
                )
            }
        )
    )

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        val card = HOME_CARDS.firstOrNull { it.id == id } ?: return
        scope.updateLocalSetting { repository.setHomeCardVisible(card.kind, checked) }
    }

    private class HomeCardRow(val id: SettingRowId, val kind: HomeCardKind, val title: StringResource)

    private companion object {
        /** The feed order is the row order. */
        val HOME_CARDS = listOf(
            HomeCardRow(SettingRowId.HOME_CARD_SCHEDULE, HomeCardKind.SCHEDULE, Res.string.settings_home_card_schedule_title),
            HomeCardRow(
                SettingRowId.HOME_CARD_SCHEDULE_CHANGES,
                HomeCardKind.SCHEDULE_CHANGES,
                Res.string.settings_home_card_schedule_changes_title
            ),
            HomeCardRow(SettingRowId.HOME_CARD_MARKS, HomeCardKind.MARKS, Res.string.settings_home_card_marks_title),
            HomeCardRow(SettingRowId.HOME_CARD_SPORT, HomeCardKind.SPORT, Res.string.settings_home_card_sport_title),
            HomeCardRow(
                SettingRowId.HOME_CARD_FRIENDS,
                HomeCardKind.FRIEND_REQUESTS,
                Res.string.settings_home_card_friends_title
            )
        )
    }
}
