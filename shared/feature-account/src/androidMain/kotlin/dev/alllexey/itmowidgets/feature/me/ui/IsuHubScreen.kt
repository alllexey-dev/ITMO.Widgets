package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_mail
import dev.alllexey.itmowidgets.shared.designsystem.ic_wifi
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/** What the ISU screen asks its host: the rows name no route, the host maps them as for [MeActions]. */
data class IsuHubActions(
    val onBack: () -> Unit = {},
    /** Сообщения ИСУ. */
    val onOpenMessages: () -> Unit = {},
    /** The Wi-Fi sheet: the network, the login and the password from ISU. */
    val onOpenWifi: () -> Unit = {},
)

/** Tags for host tests. */
object IsuHubTestTags {
    const val ROOT = "isu_hub"
    const val MESSAGES_ROW = "isu_messages_row"
    const val WIFI_ROW = "wifi_row"
}

/** `ИСУ`, pushed from Профиль (docs/features/me.md): one group with the ISU messages and the Wi-Fi. Stateless. */
@Composable
fun IsuHubScreen(actions: IsuHubActions, modifier: Modifier = Modifier) {
    ItmoDetailScreen(
        title = text(AccountStrings.me_isu_hub),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag(IsuHubTestTags.ROOT),
            contentPadding = PaddingValues(
                start = ItmoTheme.spacing.screenMargin,
                end = ItmoTheme.spacing.screenMargin,
                top = ItmoTheme.spacing.related,
                bottom = ItmoTheme.spacing.section,
            ),
        ) {
            item(key = "rows") {
                ListGroup {
                    item {
                        ListRow(
                            text(AccountStrings.me_isu_messages),
                            Modifier.testTag(IsuHubTestTags.MESSAGES_ROW),
                            subtitle = text(AccountStrings.me_isu_messages_subtitle),
                            icon = painterResource(Res.drawable.ic_mail),
                            iconTone = Tone.Neutral,
                            chevron = true,
                            onClick = actions.onOpenMessages,
                        )
                    }
                    item {
                        ListRow(
                            text(AccountStrings.me_wifi),
                            Modifier.testTag(IsuHubTestTags.WIFI_ROW),
                            subtitle = text(AccountStrings.me_wifi_subtitle),
                            icon = painterResource(Res.drawable.ic_wifi),
                            iconTone = Tone.Neutral,
                            chevron = true,
                            onClick = actions.onOpenWifi,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()
