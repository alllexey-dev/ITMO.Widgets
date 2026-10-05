package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Pull-to-refresh over scrollable [content] with the app's palette (`applyAppRefreshColors()`): indicator `primary`
 * on a `background` container. [refreshing] is true only for a refresh the user asked for (the pull, a retry); an
 * automatic refresh stays silent and the content simply updates, so a skeleton is never followed by the indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = ItmoTheme.colorScheme.background,
                color = ItmoTheme.colorScheme.primary,
            )
        },
        content = content,
    )
}
