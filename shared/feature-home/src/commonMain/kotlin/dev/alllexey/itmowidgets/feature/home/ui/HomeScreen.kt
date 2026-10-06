package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.presentation.HomeUiState
import dev.alllexey.itmowidgets.shared.designsystem.ic_home
import dev.alllexey.itmowidgets.shared.designsystem.ic_language
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.home_empty_title
import dev.alllexey.itmowidgets.shared.feature.home.home_open_my_itmo
import dev.alllexey.itmowidgets.shared.feature.home.home_open_qr
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * Test tags of [HomeScreen], read by host tests and the instrumented navigation and FAB flows; the rows and buttons
 * inside the cards carry `HomeCardTestTags`.
 */
object HomeTestTags {
    const val FEED = "home_feed"
    const val LOADING = "home_loading"
    const val EMPTY = "home_empty"
    const val WEB_FAB = "home_web_fab"
    const val QR_FAB = "home_qr_fab"

    /** The card of [kind]; one per kind, the feed's item key. */
    fun card(kind: HomeCardKind): String = "home_card_${kind.name.lowercase()}"
}

/**
 * Everything the feed can ask its host to do. Navigation, the widget pin and the notification permission stay with
 * the platform host; dismissing (the close button of a card of that kind) and refreshing go to the ViewModel through
 * [HomeRoute]. The cards get their part as `HomeCardActions`.
 */
data class HomeActions(
    val onRefresh: () -> Unit = {},
    val onLesson: (LessonDetailsArgs) -> Unit = {},
    val onPendingSport: (PendingSportDetailsArgs) -> Unit = {},
    val onOpenSport: () -> Unit = {},
    val onOpenFriends: () -> Unit = {},
    val onOpenUser: (isu: Int) -> Unit = {},
    val onHint: (HomeHint) -> Unit = {},
    val onOpenScheduleChanges: () -> Unit = {},
    val onOpenMarks: () -> Unit = {},
    val onDismiss: (HomeCardKind) -> Unit = {},
    val onOpenWeb: () -> Unit = {},
    val onOpenQr: () -> Unit = {},
) {
    internal fun forCards() = HomeCardActions(
        onLesson = onLesson,
        onPendingSport = onPendingSport,
        onOpenSport = onOpenSport,
        onOpenFriends = onOpenFriends,
        onOpenUser = onOpenUser,
        onOpenScheduleChanges = onOpenScheduleChanges,
        onOpenMarks = onOpenMarks,
        onHint = onHint,
        onDismiss = onDismiss,
    )
}

/**
 * The home feed: placeholder cards on the first load, then the cards in feed order under pull-to-refresh, or the
 * `Пока пусто` state; the MyITMO and QR buttons float over the bottom end, and the list keeps its last card clear of
 * them. Each card is drawn by the one of [renderers] that claims its kind (its producing feature's); a card no
 * renderer claims is left out. A failed refresh shows in [snackbarHostState] above the buttons. Stateless;
 * [HomeRoute] feeds it.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    renderers: List<HomeCardRenderer>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.background)) {
        when (state) {
            HomeUiState.Loading -> Skeleton(
                SkeletonStyle.Cards,
                Modifier.testTag(HomeTestTags.LOADING),
                rows = SKELETON_ROWS,
                rowHeight = SkeletonRowHeight,
            )
            is HomeUiState.Content -> AppRefreshBox(
                refreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val byKind = remember(renderers) { renderers.byKind() }
                val cards = remember(state.cards, byKind) { state.cards.filter { it.kind in byKind } }
                if (cards.isEmpty()) EmptyFeed() else Feed(cards, byKind, actions, listState)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbarHostState, Modifier.fillMaxWidth())
            QuickActions(actions, Modifier.align(Alignment.End))
        }
    }
}

/** The renderer of each kind; a kind claimed twice keeps its first renderer. */
private fun List<HomeCardRenderer>.byKind(): Map<HomeCardKind, HomeCardRenderer> =
    flatMap { renderer -> renderer.kinds.map { it to renderer } }.distinctBy { it.first }.toMap()

@Composable
private fun Feed(
    cards: List<HomeCard>,
    renderers: Map<HomeCardKind, HomeCardRenderer>,
    actions: HomeActions,
    listState: LazyListState,
) {
    val cardActions = remember(actions) { actions.forCards() }
    LazyColumn(
        Modifier.fillMaxSize().testTag(HomeTestTags.FEED),
        state = listState,
        contentPadding = PaddingValues(
            top = ItmoTheme.spacing.compact,
            bottom = ItmoTheme.spacing.fabStackClearance,
        ),
    ) {
        items(cards, key = { it.kind.name }, contentType = { it::class.simpleName }) { card ->
            renderers.getValue(card.kind).Content(card, cardActions, Modifier.testTag(HomeTestTags.card(card.kind)))
        }
    }
}

/** Scrolls, so the pull reaches the refresh box; the bottom padding keeps it centred above the buttons. */
@Composable
private fun EmptyFeed() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = ItmoTheme.spacing.fabStackClearance),
        verticalArrangement = Arrangement.Center,
    ) {
        ContentState(
            title = stringResource(Res.string.home_empty_title),
            modifier = Modifier.testTag(HomeTestTags.EMPTY),
            icon = painterResource(KitRes.drawable.ic_home),
        )
    }
}

@Composable
private fun QuickActions(actions: HomeActions, modifier: Modifier) {
    Column(
        modifier.padding(ItmoTheme.spacing.group),
        verticalArrangement = Arrangement.spacedBy(FabGap),
        horizontalAlignment = Alignment.End,
    ) {
        FloatingActionButton(
            onClick = actions.onOpenWeb,
            modifier = Modifier.testTag(HomeTestTags.WEB_FAB),
            containerColor = ItmoTheme.colorScheme.secondaryContainer,
            contentColor = ItmoTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(painterResource(KitRes.drawable.ic_language), stringResource(Res.string.home_open_my_itmo))
        }
        FloatingActionButton(
            onClick = actions.onOpenQr,
            modifier = Modifier.testTag(HomeTestTags.QR_FAB),
        ) {
            Icon(painterResource(KitRes.drawable.ic_qr_code), stringResource(Res.string.home_open_qr))
        }
    }
}

/** `fragment_home.xml`'s placeholder: three 150 dp cards. */
private const val SKELETON_ROWS = 3
private val SkeletonRowHeight = 150.dp

/** The 12 dp between the two buttons of `fragment_home.xml`. */
private val FabGap = 12.dp
