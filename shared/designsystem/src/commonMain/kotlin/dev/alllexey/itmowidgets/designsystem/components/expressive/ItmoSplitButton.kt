package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import org.jetbrains.compose.resources.painterResource

/** One variant in an [ItmoSplitButton]'s menu. */
@Immutable
data class ItmoSplitButtonOption(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A primary action with a menu of its variants (the `.ics` export and its ranges): [label] runs [onClick]; the trailing
 * half, labelled [menuLabel] for TalkBack, opens [options] while [menuExpanded]. Choosing an option closes the menu
 * ([onMenuExpandedChange] with false) and then runs it. Both halves keep a 48 dp touch target around their 40 dp look.
 * Only where a primary action has variants. Expressive in both states of the theme's switch: Material has no other
 * form of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoSplitButton(
    label: String,
    onClick: () -> Unit,
    menuLabel: String,
    options: List<ItmoSplitButtonOption>,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            WithTouchTarget {
                SplitButtonDefaults.LeadingButton(onClick = onClick, enabled = enabled) { Text(label, maxLines = 1) }
            }
        },
        trailingButton = {
            Box {
                WithTouchTarget {
                    SplitButtonDefaults.TrailingButton(
                        checked = menuExpanded,
                        onCheckedChange = onMenuExpandedChange,
                        modifier = Modifier.semantics { contentDescription = menuLabel },
                        enabled = enabled,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_expand_more),
                            contentDescription = null,
                            modifier = if (menuExpanded) Modifier.rotate(HALF_TURN) else Modifier,
                        )
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onMenuExpandedChange(false)
                                option.onClick()
                            },
                        )
                    }
                }
            }
        },
    )
}

/**
 * `SplitButtonLayout` drops the minimum interactive size inside, so each 40 dp half would be a smaller target than the
 * kit allows; this gives it back, and the halves keep their look centred in 48 dp.
 */
@Composable
private fun WithTouchTarget(content: @Composable () -> Unit) {
    val touchTarget = ItmoTheme.spacing.touchTarget
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides touchTarget, content = content)
}

private const val HALF_TURN = 180f
