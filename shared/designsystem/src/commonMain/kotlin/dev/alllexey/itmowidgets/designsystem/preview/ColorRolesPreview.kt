package dev.alllexey.itmowidgets.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoExtendedColors
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * Every colour the kit reads, scheme roles first, then the app's own colours: the golden that shows a scheme change
 * (a seed, the M3E token change) at a glance. Labels are role names, not user-visible text. The window is as tall as
 * the list at 1.3, so no row is cut off.
 */
@Preview(heightDp = 1800)
@Composable
private fun ColorRolesPreview() = ItmoPreview {
    val roles = schemeRoles(MaterialTheme.colorScheme) + extendedRoles(ItmoTheme.extendedColors)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        roles.forEach { (name, color) -> RoleRow(name, color) }
    }
}

@Composable
private fun RoleRow(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 48.dp, height = 24.dp)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        )
        Text(name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun schemeRoles(scheme: ColorScheme): List<Pair<String, Color>> = with(scheme) {
    listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceDim" to surfaceDim,
        "surfaceBright" to surfaceBright,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
    )
}

private fun extendedRoles(colors: ItmoExtendedColors): List<Pair<String, Color>> = with(colors) {
    listOf(
        "lessonTypeLecture" to lessonTypeLecture,
        "lessonTypeLab" to lessonTypeLab,
        "lessonTypePractice" to lessonTypePractice,
        "lessonTypeAssessment" to lessonTypeAssessment,
        "lessonTypeConsultation" to lessonTypeConsultation,
        "lessonTypeSport" to lessonTypeSport,
        "lessonTypeFree" to lessonTypeFree,
        "lessonTypeDefault" to lessonTypeDefault,
        "recordbookPassed" to recordbookPassed,
        "sportScoreAttendance" to sportScoreAttendance,
        "sportScoreBonus" to sportScoreBonus,
        "sportConditionAllowed" to sportConditionAllowed,
        "sportConditionAllowedContainer" to sportConditionAllowedContainer,
        "sportConditionWaiting" to sportConditionWaiting,
        "sportConditionWaitingContainer" to sportConditionWaitingContainer,
        "sportConditionWarning" to sportConditionWarning,
        "sportConditionWarningContainer" to sportConditionWarningContainer,
        "sportConditionBlocked" to sportConditionBlocked,
        "sportConditionBlockedContainer" to sportConditionBlockedContainer,
        "teacherLevelVeryNegative" to teacherLevelVeryNegative,
        "teacherLevelNegative" to teacherLevelNegative,
        "teacherLevelMixed" to teacherLevelMixed,
        "teacherLevelPositive" to teacherLevelPositive,
        "teacherLevelVeryPositive" to teacherLevelVeryPositive,
    )
}
