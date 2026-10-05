package dev.alllexey.itmowidgets.designsystem.components.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** A lesson: kind with its colour, a teacher with an id and a review tone, flow, place and the map. */
@Preview
@Composable
private fun DetailsHeaderPreview() = ItmoPreview {
    DetailsPreviewFrame {
        DetailsHeader(
            title = "Математический анализ (продвинутый уровень)",
            date = "Понедельник, 7 сентября 2026",
            time = "08:20-09:50",
            duration = "90 мин",
            kind = "Лекция · Очный",
            kindColor = ItmoTheme.extendedColors.sportConditionAllowed,
            teacher = DetailsTeacher(
                DetailsFact("Преподаватель", PreviewFixtures.ShortPersonName),
                onClick = {},
                clickLabel = "Открыть профиль",
                tone = ItmoTheme.extendedColors.teacherLevelPositive,
                toneDescription = "Тон отзывов: скорее положительные",
            ),
            flow = DetailsFact("Поток", "Тестовый поток с очень длинным названием, которое не помещается в одну строку"),
            place = DetailsFact("Место", "1506 · Кронверкский проспект, 49"),
            map = DetailsMapAction("Открыть на карте", onClick = {}),
        )
    }
}

/** Sport without a teacher id: no chevron, no map, a long name that wraps. */
@Preview
@Composable
private fun DetailsHeaderLongPreview() = ItmoPreview {
    DetailsPreviewFrame {
        DetailsHeader(
            title = PreviewFixtures.LongSubjectName,
            date = "Среда, 30 сентября 2026",
            time = "17:00-18:30",
            duration = "90 мин",
            kind = "Игровые виды спорта",
            teacher = DetailsTeacher(DetailsFact("Преподаватель", PreviewFixtures.LongPersonName)),
            place = DetailsFact("Место", "Спортивный комплекс, Ломоносова, 9, зал 2"),
        )
    }
}

/** Only what every sheet has: title, date and time. */
@Preview
@Composable
private fun DetailsHeaderMinimalPreview() = ItmoPreview {
    DetailsPreviewFrame {
        DetailsHeader(title = "Физика", date = "Пятница, 2 октября 2026", time = "10:00-11:30")
    }
}

/** On the sheet's colour and margin, as the details sheets pad their content. */
@Composable
private fun DetailsPreviewFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.group),
    ) {
        content()
    }
}
