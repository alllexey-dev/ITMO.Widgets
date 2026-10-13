package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The kit's tonal colours: the primary family (`primaryContainer` and its "on" colour), so a secondary action reads
 * as active in light and dark. Material's default `secondaryContainer` comes out as a grey pill under the app's
 * schemes and looks disabled. Disabled keeps Material's colours.
 */
@Composable
fun tonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = ItmoTheme.colorScheme.primaryContainer,
    contentColor = ItmoTheme.colorScheme.onPrimaryContainer,
)

/** A filled tonal button in [tonalButtonColors]: an active secondary action (`Записаться` in a row, `Повторить`). */
@Composable
fun TonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = tonalButtonColors(),
        contentPadding = contentPadding,
        content = content,
    )
}
