package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun ProgressButtonIdlePreview() = ItmoPreview {
    ProgressButtonColumn("Записаться", "Обновить", inProgress = false)
}

@Preview
@Composable
private fun ProgressButtonInProgressPreview() = ItmoPreview {
    ProgressButtonColumn("Записаться", "Обновить", inProgress = true)
}

@Preview
@Composable
private fun ProgressButtonDisabledPreview() = ItmoPreview {
    ProgressButtonColumn("Записаться", "Обновить", inProgress = false, enabled = false)
}

/** Every style without and with an icon, so a progress state shows both ways of keeping the geometry. */
@Composable
private fun ProgressButtonColumn(label: String, iconLabel: String, inProgress: Boolean, enabled: Boolean = true) {
    val refresh = painterResource(Res.drawable.ic_refresh)
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        ProgressButtonStyle.entries.forEach { style ->
            ProgressButton(label, onClick = {}, style = style, inProgress = inProgress, enabled = enabled)
            ProgressButton(
                iconLabel,
                onClick = {},
                style = style,
                inProgress = inProgress,
                enabled = enabled,
                icon = refresh,
            )
        }
    }
}
