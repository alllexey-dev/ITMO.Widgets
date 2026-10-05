package dev.alllexey.itmowidgets.designsystem.components.expressive

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
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive

// The kit's own components with the theme's switch on. Their switch-off baselines are their own previews
// (ContentStateLoading*, AppRefreshBox*), which stay as they are until the M3E token change.

/** `ContentStateLoading` in a section with the switch on: the morphing indicator. */
@Preview
@Composable
private fun ItmoThemeExpressiveContentStateLoadingPreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { ContentStateLoading(size = ContentStateSize.Compact) }
}

/** `AppRefreshBox` refreshing with the switch on: the contained loading indicator over the content. */
@Preview(heightDp = REFRESH_HEIGHT_DP)
@Composable
private fun ItmoThemeExpressiveAppRefreshBoxPreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) {
        AppRefreshBox(refreshing = true, onRefresh = {}) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ItmoTheme.spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                listOf(PreviewFixtures.LongSubjectName, PreviewFixtures.ShortPersonName).forEach {
                    Text(it, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private const val REFRESH_HEIGHT_DP = 240
