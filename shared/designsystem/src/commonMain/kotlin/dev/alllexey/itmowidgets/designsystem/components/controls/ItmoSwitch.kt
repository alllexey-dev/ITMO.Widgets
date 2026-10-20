package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An on/off switch: material3's `Switch` as the settings rows draw it today. With [onCheckedChange] null the switch
 * only shows [checked] and the caller's row carries the toggle (and its semantics), as `SettingsToggleRow` does.
 */
@Composable
fun ItmoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(checked, onCheckedChange, modifier, enabled = enabled)
}
