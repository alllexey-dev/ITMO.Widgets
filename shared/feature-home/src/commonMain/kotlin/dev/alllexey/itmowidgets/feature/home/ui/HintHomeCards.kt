package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCard
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCloseButton
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.designsystem.ic_star_shine
import dev.alllexey.itmowidgets.shared.designsystem.ic_widgets
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_dismiss
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_notifications_title
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_services_title
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_action
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_description
import dev.alllexey.itmowidgets.shared.feature.home.home_hint_widgets_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** The home feed's own cards, the three hints: an outlined card with the hint's action and a close button. */
object HintHomeCardRenderer : HomeCardRenderer {

    override val kinds: Set<HomeCardKind> = HomeHint.entries.map { it.kind }.toSet()

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
        check(card is HomeCard.Hint) { "${card.kind} is not a hint" }
        HintCard(card.hint, actions, modifier)
    }
}

@Composable
private fun HintCard(hint: HomeHint, actions: HomeCardActions, modifier: Modifier) {
    val content = hintContent(hint)
    FeedCard(modifier, outlined = true) {
        Row(
            Modifier.padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.cardPadding,
                end = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.cardPadding,
            ),
        ) {
            Icon(
                content.icon,
                contentDescription = null,
                modifier = Modifier.size(HintIconSize),
                tint = ItmoTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content)) {
                Text(
                    stringResource(content.title),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.titleMedium,
                )
                Text(
                    stringResource(content.text),
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
                ProgressButton(
                    label = stringResource(content.action),
                    onClick = { actions.onHint(hint) },
                    modifier = Modifier.padding(top = ItmoTheme.spacing.compact).testTag(HomeCardTestTags.HINT_ACTION),
                    style = ProgressButtonStyle.Tonal,
                )
            }
            FeedCloseButton(
                stringResource(Res.string.home_hint_dismiss),
                { actions.onDismiss(hint.kind) },
                Modifier.testTag(HomeCardTestTags.DISMISS).offset(y = -ItmoTheme.spacing.compact),
            )
        }
    }
}

private class HintContent(val icon: Painter, val title: StringResource, val text: StringResource, val action: StringResource)

@Composable
private fun hintContent(hint: HomeHint): HintContent = when (hint) {
    HomeHint.WIDGETS -> HintContent(
        painterResource(KitRes.drawable.ic_widgets),
        Res.string.home_hint_widgets_title,
        Res.string.home_hint_widgets_description,
        Res.string.home_hint_widgets_action,
    )
    HomeHint.NOTIFICATIONS -> HintContent(
        painterResource(KitRes.drawable.ic_notification),
        Res.string.home_hint_notifications_title,
        Res.string.home_hint_notifications_description,
        Res.string.home_hint_notifications_action,
    )
    HomeHint.SERVICES -> HintContent(
        painterResource(KitRes.drawable.ic_star_shine),
        Res.string.home_hint_services_title,
        Res.string.home_hint_services_description,
        Res.string.home_hint_services_action,
    )
}

private val HintIconSize = 24.dp
