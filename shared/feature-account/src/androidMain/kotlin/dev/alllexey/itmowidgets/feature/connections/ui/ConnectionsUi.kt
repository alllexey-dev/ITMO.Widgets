package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.connections.presentation.ConfirmAsk
import dev.alllexey.itmowidgets.feature.connections.presentation.DeviceIcon
import dev.alllexey.itmowidgets.feature.connections.presentation.Fact
import dev.alllexey.itmowidgets.feature.connections.presentation.Pill
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_dns
import dev.alllexey.itmowidgets.shared.designsystem.ic_globe
import dev.alllexey.itmowidgets.shared.designsystem.ic_smart_toy
import dev.alllexey.itmowidgets.shared.designsystem.ic_smartphone
import org.jetbrains.compose.resources.painterResource

/** A catalog key of this module as text. */
@Composable
internal fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

@Composable
internal fun DeviceIcon.painter(): Painter = painterResource(
    when (this) {
        DeviceIcon.PHONE -> Res.drawable.ic_smartphone
        DeviceIcon.COMPUTER -> Res.drawable.ic_computer
        DeviceIcon.AGENT -> Res.drawable.ic_dns
        DeviceIcon.MCP -> Res.drawable.ic_smart_toy
        DeviceIcon.WEB -> Res.drawable.ic_globe
    },
)

/** Clients are [Tone.Primary], installs and sessions neutral (UX spec section 2). */
internal fun DeviceIcon.tone(): Tone = when (this) {
    DeviceIcon.AGENT, DeviceIcon.MCP, DeviceIcon.WEB -> Tone.Primary
    DeviceIcon.PHONE, DeviceIcon.COMPUTER -> Tone.Neutral
}

@Composable
internal fun PillView(pill: Pill) =
    StatusPill(pill.text.asString(), tone = if (pill.error) Tone.Error else Tone.Warning)

/** The settings column of a pushed screen: margins, gaps and the navigation bar. */
@Composable
internal fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = ItmoTheme.spacing.screenMargin)
        .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group)
        .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    content = content,
)

/** «Действует до» - «10 января» rows. */
@Composable
internal fun FactsGroup(facts: List<Fact>, modifier: Modifier = Modifier) {
    if (facts.isEmpty()) return
    ListGroup(modifier) {
        facts.forEach { fact -> item { ListRow(fact.title.asString(), trailing = fact.value.asString()) } }
    }
}

@Composable
internal fun ConfirmAskDialog(ask: ConfirmAsk, onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    ask.title.asString(),
    ask.confirm.asString(),
    text(CoreStrings.common_cancel),
    onConfirm,
    onDismiss,
    text = ask.text.asString(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragHandle = null,
    containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
) { content() }
