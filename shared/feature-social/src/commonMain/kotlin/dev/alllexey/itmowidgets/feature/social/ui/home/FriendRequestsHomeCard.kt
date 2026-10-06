package dev.alllexey.itmowidgets.feature.social.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCard
import dev.alllexey.itmowidgets.designsystem.components.cards.FeedCardHeader
import dev.alllexey.itmowidgets.designsystem.components.rows.UserRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.home_friends_all
import dev.alllexey.itmowidgets.shared.feature.social.home_friends_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** The incoming friend requests home card: the count, the first [FRIENDS_LIMIT] people and `Все заявки`. */
object FriendRequestsHomeCardRenderer : HomeCardRenderer {

    /** Rows of the card; the count in the header covers all requests. */
    const val FRIENDS_LIMIT = 3

    override val kinds: Set<HomeCardKind> = setOf(HomeCardKind.FRIEND_REQUESTS)

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) {
        check(card is HomeCard.FriendRequests) { "${card.kind} is not the friend requests card" }
        FeedCard(modifier) {
            Column(Modifier.padding(top = ItmoTheme.spacing.cardPadding, bottom = ItmoTheme.spacing.compact)) {
                FeedCardHeader(
                    painterResource(KitRes.drawable.ic_group),
                    stringResource(Res.string.home_friends_title),
                    Modifier.padding(horizontal = ItmoTheme.spacing.cardPadding),
                ) {
                    Pill(card.incoming.size.toString())
                }
                Column(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                    card.incoming.take(FRIENDS_LIMIT).forEach { user ->
                        UserRow(
                            name = user.name,
                            pictureUrl = user.pictureUrl,
                            modifier = Modifier.testTag(HomeCardTestTags.FRIEND_ROW),
                            subtitle = user.primaryGroup()?.name?.takeIf { it.isNotBlank() },
                            onClick = { actions.onOpenUser(user.isu) },
                        )
                    }
                }
                ProgressButton(
                    label = stringResource(Res.string.home_friends_all),
                    onClick = actions.onOpenFriends,
                    modifier = Modifier.padding(start = ItmoTheme.spacing.compact).testTag(HomeCardTestTags.FRIENDS_ALL),
                    style = ProgressButtonStyle.Text,
                )
            }
        }
    }
}
