package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.IOS_PRESSED_ALPHA
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
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
 *
 * Under the iOS style the halves are two filled capsules of `ProgressButton`'s iOS look, the trailing one with the
 * chevron, and the options open in the iOS [ItmoMenu].
 */
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
    val menu: @Composable () -> Unit = {
        ItmoMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
            groups = listOf(options.map { ItmoMenuItem(it.label, it.onClick) }),
        )
    }
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> MaterialSplitButton(
            label, onClick, menuLabel, menuExpanded, onMenuExpandedChange, modifier, enabled, menu,
        )
        ItmoPlatformStyle.Ios -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(IosHalvesGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressButton(label, onClick, style = ProgressButtonStyle.Filled, enabled = enabled)
            Box {
                IosMenuHalf(menuLabel, menuExpanded, onMenuExpandedChange, enabled)
                menu()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialSplitButton(
    label: String,
    onClick: () -> Unit,
    menuLabel: String,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    menu: @Composable () -> Unit,
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
                menu()
            }
        },
    )
}

/**
 * The trailing half under the iOS style: the chevron on a filled capsule as high as `ProgressButton`'s, toggling the
 * menu like Material's trailing button, dimmed while pressed.
 */
@Composable
private fun IosMenuHalf(label: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = ItmoTheme.iosColors
    val container = if (enabled) ItmoTheme.colorScheme.primary else colors.tertiarySystemFill
    val content = if (enabled) ItmoTheme.colorScheme.onPrimary else colors.tertiaryLabel
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(expanded, interaction, null, enabled, Role.Button, onExpandedChange)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f }
            .heightIn(min = IosMetrics.buttonHeight)
            .background(container, CircleShape)
            .padding(horizontal = IosMetrics.buttonHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier
                .size(IosMetrics.activityIndicatorMedium)
                .then(if (expanded) Modifier.rotate(HALF_TURN) else Modifier),
            tint = content,
        )
    }
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

/** Between the two iOS capsules: they read as one control, as Material's 2 dp split does. */
private val IosHalvesGap = 2.dp
