package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import org.jetbrains.compose.resources.painterResource

/** One colour of a [SwatchChoiceDialog]: its name (read by TalkBack and shown when picked) and its fill. */
@Immutable
data class Swatch(val label: String, val color: Color, val contentColor: Color)

/**
 * Picks one of [swatches], a grid of colour circles with the name of the current one under it. A tap reports
 * [onSelect] and the dialog stays, so the caller can apply the colour at once and the user sees it before closing;
 * [confirmLabel] closes it through [onDismiss], as do back and a tap outside. Nothing animates. [below] goes under the
 * grid, inside the scrolling content: the custom colour's picker.
 */
@Composable
fun SwatchChoiceDialog(
    title: String,
    swatches: List<Swatch>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    below: (@Composable () -> Unit)? = null,
) {
    DialogWindow(onDismissRequest = onDismiss) {
        SwatchChoiceDialogSurface(title, swatches, selectedIndex, onSelect, onDismiss, confirmLabel, modifier, below)
    }
}

/** [SwatchChoiceDialog] without its window: for previews and for hosts that own the window. */
@Composable
fun SwatchChoiceDialogSurface(
    title: String,
    swatches: List<Swatch>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    below: (@Composable () -> Unit)? = null,
) {
    val content = @Composable { SwatchGrid(swatches, selectedIndex, onSelect, below) }
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> DialogSurface(
            title = title,
            modifier = modifier,
            buttons = { TextButton(onClick = onDismiss) { Text(confirmLabel) } },
            content = content,
        )
        ItmoPlatformStyle.Ios -> IosAlertSurface(
            title = title,
            buttons = listOf(IosAlertButton(confirmLabel, onDismiss, IosAlertButtonKind.Preferred)),
            modifier = modifier,
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(
    swatches: List<Swatch>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    below: (@Composable () -> Unit)?,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            maxItemsInEachRow = COLUMNS,
        ) {
            swatches.forEachIndexed { index, swatch ->
                SwatchCircle(swatch, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
        selectedIndex?.let(swatches::getOrNull)?.let { swatch ->
            Text(
                swatch.label,
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        below?.invoke()
    }
}

/** A touch target with the colour inside; the picked one is ringed in `onSurface` and checked in its content colour. */
@Composable
private fun SwatchCircle(swatch: Swatch, selected: Boolean, onClick: () -> Unit) {
    val target = ItmoTheme.spacing.touchTarget
    Box(
        Modifier
            .size(target)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(RingWidth, ItmoTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = swatch.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) target - RingInset else target - SwatchInset)
                .background(swatch.color, CircleShape)
                .border(1.dp, ItmoTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(CheckSize), tint = swatch.contentColor)
            }
        }
    }
}

private const val COLUMNS = 4
private val RingWidth = 2.dp
/** The gap between the ring and the colour of the picked swatch, both sides. */
private val RingInset = 12.dp
private val SwatchInset = 8.dp
private val CheckSize = 20.dp
