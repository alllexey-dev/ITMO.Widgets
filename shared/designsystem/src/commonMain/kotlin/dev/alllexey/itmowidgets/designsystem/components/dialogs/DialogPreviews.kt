package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun ConfirmDialogPreview() = ItmoPreview {
    DialogPreviewFrame {
        ConfirmDialogSurface(
            title = "Удалить из друзей?",
            text = "${PreviewFixtures.LongPersonName} больше не увидит ваше расписание.",
            confirmLabel = "Удалить",
            dismissLabel = "Отмена",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ConfirmDialogIconPreview() = ItmoPreview {
    DialogPreviewFrame {
        ConfirmDialogSurface(
            title = "Доступ к календарю",
            text = "Пары и спорт появятся в выбранном календаре и будут обновляться сами.",
            icon = painterResource(Res.drawable.ic_edit_calendar),
            confirmLabel = "Разрешить",
            dismissLabel = "Не сейчас",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ChoiceDialogPreview() = ItmoPreview {
    DialogPreviewFrame {
        ChoiceDialogSurface(
            title = "Размер текста",
            options = listOf("Обычный", "Крупный", "Очень крупный"),
            selectedIndex = 1,
            onSelect = {},
            onDismiss = {},
            dismissLabel = "Отмена",
        )
    }
}

@Preview
@Composable
private fun ChoiceDialogItemsPreview() = ItmoPreview {
    DialogPreviewFrame {
        ChoiceDialogSurface(
            title = "Выберите таблицу",
            options = listOf("Баллы потока (моя)", PreviewFixtures.LongSubjectName),
            selectedIndex = null,
            onSelect = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ReportDialogPreview() = ItmoPreview {
    DialogPreviewFrame { PreviewReportDialog(ReportDialogState(selectedReason = 1, comment = "Отзыв о другом преподавателе")) }
}

@Preview
@Composable
private fun ReportDialogEmptyPreview() = ItmoPreview {
    DialogPreviewFrame { PreviewReportDialog(ReportDialogState()) }
}

@Preview
@Composable
private fun ReportDialogSendingPreview() = ItmoPreview {
    DialogPreviewFrame { PreviewReportDialog(ReportDialogState(selectedReason = 3, sending = true)) }
}

@Preview
@Composable
private fun ReportDialogErrorPreview() = ItmoPreview {
    DialogPreviewFrame {
        PreviewReportDialog(
            ReportDialogState(selectedReason = 0, comment = "Оскорбления", error = "Нет связи. Проверьте интернет."),
        )
    }
}

@Composable
private fun PreviewReportDialog(state: ReportDialogState) {
    ReportDialogSurface(
        title = "Пожаловаться на отзыв",
        reasons = listOf("Оскорбления", "Не о том преподавателе", "Спам", "Другое"),
        state = state,
        commentLabel = "Комментарий (необязательно)",
        sendLabel = "Отправить",
        dismissLabel = "Отмена",
        onReasonSelect = {},
        onCommentChange = {},
        onSend = {},
        onDismiss = {},
    )
}

/** The dialog on the screen margin, as the window centres it. */
@Composable
private fun DialogPreviewFrame(content: @Composable () -> Unit) {
    Box(Modifier.padding(ItmoTheme.spacing.section)) { content() }
}
