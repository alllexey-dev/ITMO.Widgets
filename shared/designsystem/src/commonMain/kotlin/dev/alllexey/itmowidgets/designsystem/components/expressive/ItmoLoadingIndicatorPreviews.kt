package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive

/** The switch off: today's circular indicator, the same in both forms. */
@Preview
@Composable
private fun ItmoLoadingIndicatorStandardPreview() = ItmoPreview { Indicators() }

/** The switch on: the morphing indicator, plain and contained. */
@Preview
@Composable
private fun ItmoLoadingIndicatorExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { Indicators() }
}

@Composable
private fun Indicators() {
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItmoLoadingIndicator()
        ItmoLoadingIndicator(contained = true)
    }
}
