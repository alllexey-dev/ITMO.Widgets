package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.title_sport_my
import dev.alllexey.itmowidgets.shared.feature.sport.title_sport_sign
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two pages of the sport tab, in tab order; [ordinal] is the page index. */
enum class SportPage(internal val title: StringResource) {
    MY(Res.string.title_sport_my),
    SIGN(Res.string.title_sport_sign),
    ;

    companion object {
        fun at(index: Int): SportPage = entries[index]
    }
}

/** Tags for host tests and the instrumented flows. */
object SportScreenTestTags {
    const val TABS = "sport_tabs"
    const val PAGER = "sport_pager"

    fun tab(page: SportPage): String = "sport_tab_${page.name.lowercase()}"
}

/** The pager of [SportScreen], on `Мой спорт` unless [initialPage] says otherwise; it survives recreation. */
@Composable
fun rememberSportPagerState(initialPage: SportPage = SportPage.MY): PagerState =
    rememberPagerState(initialPage = initialPage.ordinal) { SportPage.entries.size }

/**
 * The sport tab: the secondary tabs `Мой спорт` and `Запись` over a pager whose pages are [page]. A tap on a tab
 * slides to its page; a swipe moves one page and, through `tabSwipeHandover`, keeps its fling inside the pager, so
 * only a new swipe at either end moves the bottom tabs around it. Both pages stay composed, as the View pager kept the
 * neighbour alive, so each keeps its list position and its open dialog while the other is in front.
 *
 * Stateless: [pagerState] is the host's. `SportRoute` puts `SportMyScreen` and `SportSignScreen` in the pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportScreen(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    page: @Composable (SportPage) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SportScreenTestTags.TABS),
            containerColor = ItmoTheme.colorScheme.surface,
            contentColor = ItmoTheme.colorScheme.onSurface,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(pagerState.currentPage),
                    color = ItmoTheme.colorScheme.primary,
                )
            },
        ) {
            SportPage.entries.forEach { tab ->
                Tab(
                    selected = pagerState.currentPage == tab.ordinal,
                    onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                    modifier = Modifier.testTag(SportScreenTestTags.tab(tab)),
                    selectedContentColor = ItmoTheme.colorScheme.onSurface,
                    unselectedContentColor = ItmoTheme.colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(tab.title), style = ItmoTheme.typography.titleMedium, maxLines = 1) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(SportScreenTestTags.PAGER)
                .tabSwipeHandover(pagerState),
            beyondViewportPageCount = SportPage.entries.size - 1,
            key = { SportPage.at(it).name },
        ) { index ->
            page(SportPage.at(index))
        }
    }
}
