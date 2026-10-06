package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * One step of the flow: a page that scrolls on its own, its [title] in `headlineSmall`, an optional [subtitle] below
 * it, then [content] (the `NestedScrollView` and its padded column of the step layouts).
 */
@Composable
internal fun OnboardingPage(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = ItmoTheme.spacing.screenMargin,
                top = ItmoTheme.spacing.compact,
                end = ItmoTheme.spacing.screenMargin,
                bottom = ItmoTheme.spacing.group,
            ),
    ) {
        Text(
            title,
            Modifier.semantics { heading() },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.headlineSmall,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.padding(top = ItmoTheme.spacing.compact),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        content()
    }
}

/** `Widget.ItmoWidgets.Card.Content`: a filled `surfaceContainerLow` card with the content shape and no stroke. */
@Composable
internal fun OnboardingCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier,
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}
