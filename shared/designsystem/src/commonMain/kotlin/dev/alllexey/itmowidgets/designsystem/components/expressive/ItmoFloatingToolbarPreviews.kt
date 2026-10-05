package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import org.jetbrains.compose.resources.painterResource

/** The switch off: three actions. */
@Preview
@Composable
private fun ItmoFloatingToolbarStandardPreview() = ItmoPreview { Toolbar() }

/** The switch on. */
@Preview
@Composable
private fun ItmoFloatingToolbarExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { Toolbar() }
}

@Composable
private fun Toolbar() {
    ItmoFloatingToolbar(
        actions = listOf(
            ItmoToolbarAction("Найти", painterResource(Res.drawable.ic_search), onClick = {}),
            ItmoToolbarAction("Добавить в календарь", painterResource(Res.drawable.ic_calendar_add), onClick = {}),
            ItmoToolbarAction("Поделиться", painterResource(Res.drawable.ic_share), onClick = {}),
        ),
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}
