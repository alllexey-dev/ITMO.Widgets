package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsToggleRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_sample
import dev.alllexey.itmowidgets.shared.feature.settings.settings_appearance_sample_action
import dev.alllexey.itmowidgets.shared.feature.settings.settings_home_card_schedule_changes_title
import org.jetbrains.compose.resources.stringResource

/**
 * A few of the app's controls in the colours it draws now, above the appearance rows: a group with a switch, a filled
 * and a tonal button. Every choice recolours the whole app at once, so the sample needs no state of its own. It is a
 * picture: TalkBack reads it as one element and its controls do nothing.
 */
@Composable
internal fun AppearanceSample(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.settings_appearance_sample)
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        SettingsGroup {
            row("switch") {
                SettingsToggleRow(
                    stringResource(Res.string.settings_home_card_schedule_changes_title),
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            Button(onClick = {}) { Text(stringResource(Res.string.settings_appearance_sample_action)) }
            FilledTonalButton(onClick = {}) { Text(stringResource(CoreRes.string.common_cancel)) }
        }
    }
}
