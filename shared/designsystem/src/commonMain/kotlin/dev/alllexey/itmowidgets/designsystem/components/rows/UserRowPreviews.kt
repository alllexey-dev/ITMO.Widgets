package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.settings.SelectionMode
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** A friend that opens the profile, an incoming request with two actions, a sent request in flight. */
@Preview
@Composable
private fun UserRowPreview() = ItmoPreview {
    UserCard {
        UserRow(PreviewFixtures.ShortPersonName, pictureUrl = null, subtitle = "P3119", onClick = {})
        UserRow(
            "Петров Алексей",
            pictureUrl = null,
            subtitle = "367123, P3124",
            onClick = {},
            primaryAction = UserRowAction("Принять") {},
            secondaryAction = UserRowAction("Отклонить") {},
        )
        UserRow(
            "Смирнова Анна",
            pictureUrl = null,
            subtitle = "368001, M3207",
            status = "Заявка отправлена",
            primaryAction = UserRowAction("Отменить") {},
            busy = true,
        )
    }
}

/** A long name wraps, a long subtitle stays on one line. */
@Preview
@Composable
private fun UserRowLongTextPreview() = ItmoPreview {
    UserCard {
        UserRow(
            PreviewFixtures.LongPersonName,
            pictureUrl = null,
            subtitle = PreviewFixtures.LongSubjectName,
            status = "Заявка отправлена",
            onClick = {},
        )
        UserRow(
            PreviewFixtures.LongPersonName,
            pictureUrl = null,
            subtitle = "367123, P3124",
            primaryAction = UserRowAction("Добавить") {},
        )
    }
}

/** Picked, not picked, and a closed profile that cannot be picked. */
@Preview
@Composable
private fun UserRowSelectionPreview() = ItmoPreview {
    UserCard {
        UserSelectionRow(PreviewFixtures.ShortPersonName, pictureUrl = null, selected = true, onSelect = {})
        UserSelectionRow(
            PreviewFixtures.LongPersonName,
            pictureUrl = null,
            selected = false,
            onSelect = {},
            subtitle = "P3119",
        )
        UserSelectionRow(
            "Смирнова Анна",
            pictureUrl = null,
            selected = true,
            onSelect = null,
            status = "Расписание скрыто",
            mode = SelectionMode.Multiple,
        )
    }
}

@Composable
private fun UserCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(ItmoTheme.spacing.screenMargin)
            .clip(ItmoTheme.shapes.cardContent)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}
