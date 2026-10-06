package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** On and off, enabled and disabled. */
@Preview
@Composable
private fun ItmoSwitchPreview() = ItmoPreview {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        listOf(true, false).forEach { enabled ->
            Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group)) {
                ItmoSwitch(checked = true, onCheckedChange = {}, enabled = enabled)
                ItmoSwitch(checked = false, onCheckedChange = {}, enabled = enabled)
            }
        }
    }
}
