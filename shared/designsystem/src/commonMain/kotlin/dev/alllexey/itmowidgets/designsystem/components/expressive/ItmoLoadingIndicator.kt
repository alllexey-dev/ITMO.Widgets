package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicatorSize
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * An indeterminate wait shorter than about 5 s (a section, a sheet, a dialog). With [ItmoTheme.expressive] it is the
 * morphing `LoadingIndicator` in `primary`, or the contained one when [contained], both over [ItmoLoadingShapes] so the
 * shape turns about its own centre; otherwise today's circular indicator in `primary`. Never inside a button
 * (`ProgressButton` keeps its own) and never instead of a first-load skeleton.
 *
 * Under the iOS style it is the large `UIActivityIndicatorView` ([ItmoActivityIndicator]) in both forms and whatever
 * the expressive switch says: iOS has no contained or morphing indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) {
    when {
        ItmoTheme.platformStyle == ItmoPlatformStyle.Ios ->
            ItmoActivityIndicator(modifier, size = ItmoActivityIndicatorSize.Large)
        !ItmoTheme.expressive -> CircularProgressIndicator(modifier, color = ItmoTheme.colorScheme.primary)
        contained -> ContainedLoadingIndicator(modifier, polygons = ItmoLoadingShapes.indeterminate)
        else -> LoadingIndicator(
            modifier,
            color = ItmoTheme.colorScheme.primary,
            polygons = ItmoLoadingShapes.indeterminate,
        )
    }
}
