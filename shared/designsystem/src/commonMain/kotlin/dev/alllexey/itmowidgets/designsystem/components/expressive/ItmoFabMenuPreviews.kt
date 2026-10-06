package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_language
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import org.jetbrains.compose.resources.painterResource

/** The switch off, collapsed: the toggle alone. */
@Preview
@Composable
private fun ItmoFabMenuStandardPreview() = ItmoPreview { FabMenu() }

/** The switch on, collapsed. */
@Preview
@Composable
private fun ItmoFabMenuExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { FabMenu() }
}

@Composable
private fun FabMenu() {
    ItmoFabMenu(
        items = listOf(
            ItmoFabMenuItem("Мой ИТМО", painterResource(Res.drawable.ic_language), onClick = {}),
            ItmoFabMenuItem("QR-пропуск", painterResource(Res.drawable.ic_qr_code), onClick = {}),
        ),
        expanded = false,
        onExpandedChange = {},
        icon = painterResource(Res.drawable.ic_add),
        label = "Быстрые действия",
        modifier = Modifier.padding(ItmoTheme.spacing.screenMargin),
    )
}
