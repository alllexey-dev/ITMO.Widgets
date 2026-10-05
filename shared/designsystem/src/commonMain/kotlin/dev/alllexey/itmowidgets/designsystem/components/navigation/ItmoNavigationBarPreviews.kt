package dev.alllexey.itmowidgets.designsystem.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_account_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_account_circle_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_home
import dev.alllexey.itmowidgets.shared.designsystem.ic_home_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule_filled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The app's five tabs on the home tab, as the shell starts. */
@Preview
@Composable
private fun ItmoNavigationBarPreview() = ItmoPreview {
    AppTabs(listOf("Зачётка", "Расписание", "Главная", "Спорт", "Профиль"), selected = 2)
}

/** The longest label selected at the edge. */
@Preview
@Composable
private fun ItmoNavigationBarSchedulePreview() = ItmoPreview {
    AppTabs(listOf("Зачётка", "Расписание", "Главная", "Спорт", "Профиль"), selected = 1)
}

@Composable
private fun AppTabs(labels: List<String>, selected: Int) {
    ItmoNavigationBar {
        TabIcons.forEachIndexed { index, tab ->
            ItmoNavigationBarItem(
                selected = index == selected,
                onClick = {},
                label = labels[index],
                icon = painterResource(tab.icon),
                selectedIcon = painterResource(tab.selectedIcon),
            )
        }
    }
}

private class TabIcon(val icon: DrawableResource, val selectedIcon: DrawableResource)

/** `menu/bottom_nav.xml`'s order: recordbook, schedule, home, sport, me. */
private val TabIcons = listOf(
    TabIcon(Res.drawable.ic_menu_book, Res.drawable.ic_menu_book_filled),
    TabIcon(Res.drawable.ic_schedule, Res.drawable.ic_schedule_filled),
    TabIcon(Res.drawable.ic_home, Res.drawable.ic_home_filled),
    TabIcon(Res.drawable.ic_exercise, Res.drawable.ic_exercise_filled),
    TabIcon(Res.drawable.ic_account_circle, Res.drawable.ic_account_circle_filled),
)
