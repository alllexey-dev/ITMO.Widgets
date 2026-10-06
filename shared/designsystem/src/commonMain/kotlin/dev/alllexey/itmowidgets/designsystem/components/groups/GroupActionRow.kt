package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * The last row of a connected group that leads further (`item_group_action_row.xml`): `Все ссылки, N`,
 * `Все пары, N`, `Добавить ссылку`. A decorative [icon], the [text] in `bodyLarge` and a chevron; the whole row is
 * the target. Pass `Modifier.connectedGroupItem(...)` in [modifier] so the ripple keeps the row's shape.
 *
 * Under the iOS style it is a cell of an inset group that leads further: the icon in the tint, the text in body, the
 * disclosure indicator, UIKit's row height and margins, the pressed cell instead of a ripple.
 */
@Composable
fun GroupActionRow(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosGroupActionRow(text, icon, onClick, modifier)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = GroupRowMinHeight)
            .clickable(onClick = onClick)
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.compact,
                end = ItmoTheme.spacing.content,
                bottom = ItmoTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(end = ItmoTheme.spacing.cardPadding).size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyLarge,
        )
        Icon(
            painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.padding(start = ItmoTheme.spacing.compact).size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IosGroupActionRow(text: String, icon: Painter, onClick: () -> Unit, modifier: Modifier) {
    IosListRow(separatorInset = IosMetrics.separatorInsetWithIcon) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = IosMetrics.rowMinHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = IosMetrics.rowHorizontalPadding, vertical = IosMetrics.rowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = IosMetrics.rowHorizontalPadding).size(IconSize),
                tint = ItmoTheme.colorScheme.primary,
            )
            Text(
                text,
                Modifier.weight(1f),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
            IosDisclosure()
        }
    }
}

/** A connected group's row is at least 56 dp high (`docs/design.md` § Connected groups). */
private val GroupRowMinHeight = 56.dp

/** The 24 dp icons of `item_group_action_row.xml`. */
private val IconSize = 24.dp
