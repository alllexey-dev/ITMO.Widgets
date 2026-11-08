package dev.alllexey.itmowidgets.feature.finances.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.text.ContractList
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ContractSections
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.StaleContent
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionContent
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_payments
import dev.alllexey.itmowidgets.shared.feature.finances.FinancesStrings
import org.jetbrains.compose.resources.painterResource

/** What the tuition screen asks of its route. */
data class TuitionActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** `Оплатить`: my.itmo's tuition page in the system browser. */
    val onPay: () -> Unit = {},
    val onTogglePastYears: () -> Unit = {},
)

/**
 * `Оплата обучения` (docs/features/finances.md): each Договор об обучении with its balance, `Оплатить` and its
 * Платежи, earlier years under `Прошлые годы`; the stored answer dimmed under `Обновлено 2 ч назад` when my.itmo did
 * not answer. Stateless; [TuitionRoute] feeds it.
 */
@Composable
fun TuitionScreen(
    state: TuitionUiState,
    actions: TuitionActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ItmoDetailScreen(
        title = text(FinancesStrings.tuition_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppRefreshBox(refreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                        start = ItmoTheme.spacing.screenMargin,
                        end = ItmoTheme.spacing.screenMargin,
                        top = ItmoTheme.spacing.related,
                        bottom = ItmoTheme.spacing.section,
                    ),
                ) {
                    when (val content = state.content) {
                        TuitionContent.Loading -> ContentStateLoading()
                        is TuitionContent.Failed -> ContentState(
                            text(CoreStrings.common_load_error_title),
                            icon = painterResource(Res.drawable.ic_error),
                            description = content.text.asString(),
                            action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
                        )
                        TuitionContent.NoContracts -> ContentState(
                            text(FinancesStrings.tuition_none_title),
                            icon = painterResource(Res.drawable.ic_payments),
                            description = text(FinancesStrings.tuition_none_text),
                        )
                        is TuitionContent.Shown -> {
                            val stale = state.stale
                            if (stale != null) {
                                StaleContent(stale.asString()) { Contracts(content.contracts, actions) }
                            } else {
                                Contracts(content.contracts, actions)
                            }
                        }
                    }
                }
            }
            ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Contracts(contracts: ContractList, actions: TuitionActions) {
    Column {
        ContractSections(contracts, onPay = actions.onPay, onTogglePastYears = actions.onTogglePastYears)
    }
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()
