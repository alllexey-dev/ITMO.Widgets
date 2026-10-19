package dev.alllexey.itmowidgets.designsystem.components.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.services.ServicesStoredData
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import org.jetbrains.compose.resources.painterResource

/** Test tags of the consent sheet. */
object ServicesConsentTags {
    const val SHEET = "services_consent"
    const val CONNECT = "services_consent_connect"
    const val LATER = "services_consent_later"
}

/**
 * The consent before connecting to ITMO.Widgets (DECISIONS.md: enabling always goes through it), the one sheet of
 * «Сервер ITMO.Widgets», the gates that link there and the first-run step. [onConnect] is the only way the opt-in
 * turns on; [onDismiss] (`Не сейчас`, the close button, a swipe) leaves it off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesConsentSheet(onConnect: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        ServicesConsentContent(onConnect, onDismiss)
    }
}

/** The consent sheet's body: what the server checks, what it will keep, who sees what first, the two answers. */
@Composable
fun ServicesConsentContent(onConnect: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val close = text(CoreStrings.common_close)
    SheetScaffold(
        title = text(CoreStrings.services_consent_title),
        modifier = modifier.testTag(ServicesConsentTags.SHEET),
        close = SheetClose(close, onDismiss),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Text(
                text(CoreStrings.services_consent_text),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyLarge,
            )
            SectionHeader(text(CoreStrings.services_consent_stored))
            StoredDataGroup()
            ItmoCard(tone = Tone.Primary) {
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.ic_lock), contentDescription = null)
                    Text(text(CoreStrings.services_consent_privacy), style = ItmoTheme.typography.bodyMedium)
                }
            }
            ProgressButton(
                label = text(CoreStrings.services_consent_connect),
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MainButtonHeight)
                    .padding(top = ItmoTheme.spacing.compact)
                    .testTag(ServicesConsentTags.CONNECT),
            )
            ProgressButton(
                label = text(CoreStrings.services_consent_later),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag(ServicesConsentTags.LATER),
                style = ProgressButtonStyle.Text,
            )
        }
    }
}

/** What the server keeps, one row each ([ServicesStoredData]). */
@Composable
fun StoredDataGroup(modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    ListGroup(modifier) {
        ServicesStoredData.items.forEach { datum ->
            item { ListRow(datum.text.asString(), icon = painterResource(datum.icon.drawable)) }
        }
        if (extra != null) item(extra)
    }
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

private val MainButtonHeight = 56.dp
