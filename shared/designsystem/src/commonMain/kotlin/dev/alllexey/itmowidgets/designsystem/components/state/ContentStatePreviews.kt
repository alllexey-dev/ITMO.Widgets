package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import org.jetbrains.compose.resources.painterResource

@Preview(heightDp = FULL_HEIGHT_DP)
@Composable
private fun ContentStateEmptyPreview() = ItmoPreview {
    ContentState(title = "Занятий нет", icon = painterResource(Res.drawable.ic_event_note))
}

@Preview(heightDp = FULL_HEIGHT_DP)
@Composable
private fun ContentStateErrorPreview() = ItmoPreview {
    ContentState(
        title = "Не удалось загрузить расписание",
        icon = painterResource(Res.drawable.ic_error),
        description = "Проверьте подключение к интернету",
        action = ContentStateAction("Повторить", onClick = {}),
    )
}

@Preview(heightDp = FULL_HEIGHT_DP)
@Composable
private fun ContentStatePrimaryActionPreview() = ItmoPreview {
    ContentState(
        title = "Друзей пока нет",
        icon = painterResource(Res.drawable.ic_group),
        action = ContentStateAction("Найти людей", onClick = {}, style = ContentStateActionStyle.Filled),
    )
}

@Preview(heightDp = FULL_HEIGHT_DP)
@Composable
private fun ContentStateLongTextPreview() = ItmoPreview {
    ContentState(
        title = PreviewFixtures.LongSubjectName,
        icon = painterResource(Res.drawable.ic_error),
        description = "${PreviewFixtures.LongPersonName} закрыла расписание от всех, кроме друзей",
        action = ContentStateAction("Повторить", onClick = {}),
    )
}

@Preview
@Composable
private fun ContentStateCompactPreview() = ItmoPreview {
    ContentState(
        title = "Занятий нет",
        size = ContentStateSize.Compact,
        icon = painterResource(Res.drawable.ic_event_note),
        description = "Попробуйте выбрать другой день или изменить фильтры",
    )
}

@Preview
@Composable
private fun ContentStateCompactTitleOnlyPreview() = ItmoPreview {
    ContentState(title = "Заявок нет", size = ContentStateSize.Compact)
}

@Preview(heightDp = FULL_HEIGHT_DP)
@Composable
private fun ContentStateLoadingPreview() = ItmoPreview {
    ContentStateLoading()
}

@Preview
@Composable
private fun ContentStateLoadingCompactPreview() = ItmoPreview {
    ContentStateLoading(size = ContentStateSize.Compact)
}

/** A content area shorter than the capture window, so full states stay small goldens. */
private const val FULL_HEIGHT_DP = 480
