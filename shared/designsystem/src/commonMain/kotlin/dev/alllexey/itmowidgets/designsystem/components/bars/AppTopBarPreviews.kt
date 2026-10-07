package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun AppTopBarBackPreview() = ItmoPreview {
    AppTopBar(
        title = "Друзья",
        navigation = { AppTopBarBack("Назад", onClick = {}) },
        actions = { AppTopBarAction(painterResource(Res.drawable.ic_search), "Найти людей", onClick = {}) },
    )
}

@Preview
@Composable
private fun AppTopBarClosePreview() = ItmoPreview {
    AppTopBar(
        title = "Занятие",
        navigation = { AppTopBarAction(painterResource(Res.drawable.ic_close), "Закрыть", onClick = {}) },
        actions = {
            AppTopBarAction(painterResource(Res.drawable.ic_share), "Поделиться", onClick = {})
            AppTopBarAction(painterResource(Res.drawable.ic_search), "Найти", onClick = {})
        },
    )
}

@Preview
@Composable
private fun AppTopBarTitleOnlyPreview() = ItmoPreview {
    AppTopBar(title = "Настройки")
}

@Preview
@Composable
private fun AppTopBarLongTitlePreview() = ItmoPreview {
    AppTopBar(
        title = PreviewFixtures.LongSubjectName,
        navigation = { AppTopBarBack("Назад", onClick = {}) },
        actions = { AppTopBarAction(painterResource(Res.drawable.ic_share), "Поделиться", onClick = {}) },
    )
}

/** A back button with the previous screen's title, a text action and the separator of content scrolled beneath. */
@Preview
@Composable
private fun AppTopBarScrolledPreview() = ItmoPreview {
    AppTopBar(
        title = "Занятие",
        navigation = { AppTopBarBack("Назад", onClick = {}, title = "Расписание") },
        actions = { AppTopBarTextAction("Готово", onClick = {}) },
        scrolledUnder = true,
    )
}

/** The My ITMO page's bar: close, a one-line title over the current host, reload. */
@Preview
@Composable
private fun AppTopBarSubtitlePreview() = ItmoPreview {
    AppTopBar(
        title = "My ITMO",
        subtitle = "my.itmo.ru",
        navigation = { AppTopBarAction(painterResource(Res.drawable.ic_close), "Закрыть", onClick = {}) },
        actions = { AppTopBarAction(painterResource(Res.drawable.ic_refresh), "Обновить", onClick = {}) },
    )
}

/** A long title and a long subtitle, each kept to one ellipsized line. */
@Preview
@Composable
private fun AppTopBarLongSubtitlePreview() = ItmoPreview {
    AppTopBar(
        title = PreviewFixtures.LongSubjectName,
        subtitle = PreviewFixtures.LongSubjectName,
        navigation = { AppTopBarBack("Назад", onClick = {}) },
        actions = { AppTopBarAction(painterResource(Res.drawable.ic_search), "Найти", onClick = {}) },
    )
}
