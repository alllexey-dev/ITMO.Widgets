package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** Both sizes in the default colour, then the medium one in `primary` as a tinted surface would ask for it. */
@Preview
@Composable
private fun ItmoActivityIndicatorPreview() = ItmoPreview {
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItmoActivityIndicator(size = ItmoActivityIndicatorSize.Medium)
        ItmoActivityIndicator(size = ItmoActivityIndicatorSize.Large)
        ItmoActivityIndicator(color = ItmoTheme.colorScheme.primary)
    }
}
