package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun AppTopBarBackPreview() = ItmoPreview {
    AppTopBar(
        title = "Друзья",
        navigation = { AppTopBarAction(painterResource(Res.drawable.ic_arrow_back), "Назад", onClick = {}) },
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
        navigation = { AppTopBarAction(painterResource(Res.drawable.ic_arrow_back), "Назад", onClick = {}) },
        actions = { AppTopBarAction(painterResource(Res.drawable.ic_share), "Поделиться", onClick = {}) },
    )
}
