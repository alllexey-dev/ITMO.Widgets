package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive

/** The switch off: today's flat bar and ring at 60 %. */
@Preview
@Composable
private fun ItmoWavyProgressStandardPreview() = ItmoPreview { Progress() }

/** The switch on: the wavy bar and ring at 60 %. */
@Preview
@Composable
private fun ItmoWavyProgressExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { Progress() }
}

@Composable
private fun Progress() {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItmoWavyProgress(progress = { PROGRESS }, modifier = Modifier.fillMaxWidth())
        ItmoWavyProgress(progress = { PROGRESS }, shape = ItmoWavyProgressShape.Circular)
    }
}

private const val PROGRESS = 0.6f
