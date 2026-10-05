package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

@Preview(heightDp = HEIGHT_DP)
@Composable
private fun AppRefreshBoxIdlePreview() = ItmoPreview {
    AppRefreshBox(refreshing = false, onRefresh = {}) { PreviewRows() }
}

/** A refresh the user asked for: the indicator over the content that stays. */
@Preview(heightDp = HEIGHT_DP)
@Composable
private fun AppRefreshBoxRefreshingPreview() = ItmoPreview {
    AppRefreshBox(refreshing = true, onRefresh = {}) { PreviewRows() }
}

@Composable
private fun PreviewRows() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        listOf(PreviewFixtures.LongSubjectName, PreviewFixtures.ShortPersonName, PreviewFixtures.LongPersonName).forEach {
            Text(it, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
        }
    }
}

private const val HEIGHT_DP = 240
