package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * An indeterminate wait shorter than about 5 s (a section, a sheet, a dialog). With [ItmoTheme.expressive] it is the
 * morphing `LoadingIndicator` in `primary`, or the contained one when [contained]; otherwise today's circular indicator
 * in `primary`. Never inside a button (`ProgressButton` keeps its own) and never instead of a first-load skeleton.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) {
    when {
        !ItmoTheme.expressive -> CircularProgressIndicator(modifier, color = ItmoTheme.colorScheme.primary)
        contained -> ContainedLoadingIndicator(modifier)
        else -> LoadingIndicator(modifier, color = ItmoTheme.colorScheme.primary)
    }
}
