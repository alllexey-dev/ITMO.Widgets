package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The five teacher-level tones, then the empty slot of a teacher without a tone yet. */
@Preview
@Composable
private fun ToneDotPreview() = ItmoPreview {
    val colors = ItmoTheme.extendedColors
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        listOf(
            colors.teacherLevelVeryNegative,
            colors.teacherLevelNegative,
            colors.teacherLevelMixed,
            colors.teacherLevelPositive,
            colors.teacherLevelVeryPositive,
            null,
        ).forEach { ToneDot(it) }
    }
}
