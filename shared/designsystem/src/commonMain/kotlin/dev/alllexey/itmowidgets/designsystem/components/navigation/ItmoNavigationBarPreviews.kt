package dev.alllexey.itmowidgets.designsystem.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import org.jetbrains.compose.resources.painterResource

/** The five tabs on Сегодня, as the shell starts. */
@Preview
@Composable
private fun ItmoNavigationBarPreview() = ItmoPreview { AppTabs(AppTab.HOME) }

/** The longest label selected. */
@Preview
@Composable
private fun ItmoNavigationBarSchedulePreview() = ItmoPreview { AppTabs(AppTab.SCHEDULE) }

@Composable
private fun AppTabs(selected: AppTab) {
    ItmoNavigationBar {
        AppTab.entries.forEach { tab ->
            ItmoNavigationBarItem(
                selected = tab == selected,
                onClick = {},
                label = Labels.getValue(tab),
                icon = painterResource(tab.icon.drawable),
                selectedIcon = painterResource(tab.selectedIcon.drawable),
            )
        }
    }
}

/** The catalog's tab names; the kit has no catalog of its own. */
private val Labels = mapOf(
    AppTab.HOME to "Сегодня",
    AppTab.SCHEDULE to "Расписание",
    AppTab.RECORDBOOK to "Зачётка",
    AppTab.SPORT to "Спорт",
    AppTab.ME to "Профиль",
)
