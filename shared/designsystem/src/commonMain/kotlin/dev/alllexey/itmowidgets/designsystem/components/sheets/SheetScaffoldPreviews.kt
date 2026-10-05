package dev.alllexey.itmowidgets.designsystem.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupActionRow
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import org.jetbrains.compose.resources.painterResource

/** The links sheet: handle, title over the subject, close at the end, a list and a footer action. */
@Preview
@Composable
private fun SheetScaffoldPreview() = ItmoPreview {
    SheetPreviewSurface {
        SheetScaffold(
            title = "Ссылки",
            subtitle = "Математический анализ",
            close = SheetClose("Закрыть", onClick = {}),
            footer = {
                GroupActionRow("Добавить ссылку", painterResource(Res.drawable.ic_add), onClick = {})
            },
        ) {
            listOf("Курс на Moodle", "Таблица баллов потока", "Записи лекций").forEach { title ->
                GroupActionRow(title, painterResource(Res.drawable.ic_link), onClick = {})
            }
        }
    }
}

/** The form mode of the review editor: no handle, the close button centred on the title and the teacher. */
@Preview
@Composable
private fun SheetScaffoldFormPreview() = ItmoPreview {
    SheetPreviewSurface {
        SheetScaffold(
            title = "Новый отзыв",
            subtitle = PreviewFixtures.LongPersonName,
            handle = false,
            close = SheetClose("Закрыть редактор", onClick = {}),
            footer = {
                ProgressButton(
                    "Отправить",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin),
                    style = ProgressButtonStyle.Filled,
                )
            },
        ) {
            PreviewText("Текст отзыва появится здесь. Он может занимать несколько строк и переносится по словам.")
        }
    }
}

/** Loading in the bounded area: 288 dp reserved, so the choices that follow do not move the sheet. */
@Preview
@Composable
private fun SheetScaffoldLoadingPreview() = ItmoPreview {
    SheetPreviewSurface {
        SheetScaffold(
            title = "Баллы из таблицы",
            subtitle = PreviewFixtures.LongSubjectName,
            contentMinHeight = 288.dp,
        ) {
            ContentStateLoading(size = ContentStateSize.Compact)
        }
    }
}

/** The details sheets' toolbar: close before the title in a 56 dp bar. */
@Preview
@Composable
private fun SheetScaffoldToolbarPreview() = ItmoPreview {
    SheetPreviewSurface {
        SheetScaffold(
            title = "Пара",
            close = SheetClose("Закрыть", onClick = {}),
            closePlacement = SheetClosePlacement.Start,
        ) {
            PreviewText(PreviewFixtures.LongSubjectName)
        }
    }
}

/** A sheet colour under the body, as `BottomSheetDialogFragment` draws it. */
@Composable
private fun SheetPreviewSurface(content: @Composable () -> Unit) {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}

@Composable
private fun ColumnScope.PreviewText(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.bodyLarge,
    )
}
