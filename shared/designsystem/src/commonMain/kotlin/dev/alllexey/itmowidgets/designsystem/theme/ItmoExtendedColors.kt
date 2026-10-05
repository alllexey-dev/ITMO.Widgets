package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own colours beside the Material scheme, one slot per entry of `res/values{,-night}/colors.xml`
 * (`DesignTokensParityTest` keeps them equal). The widget palette, `shortcut_icon_background` and `calendar_app` stay
 * in resources only: widgets, launcher shortcuts and the device calendar never render Compose.
 */
@Immutable
data class ExtendedColorTokens(
    val lessonTypeLecture: Color,
    val lessonTypeLab: Color,
    val lessonTypePractice: Color,
    val lessonTypeAssessment: Color,
    val lessonTypeConsultation: Color,
    val lessonTypeSport: Color,
    val lessonTypeFree: Color,
    val lessonTypeDefault: Color,
    val recordbookPassed: Color,
    val sportScoreAttendance: Color,
    val sportScoreBonus: Color,
    val sportConditionAllowed: Color,
    val sportConditionWaiting: Color,
    val sportConditionWarning: Color,
    val sportConditionBlocked: Color,
    val teacherLevelVeryNegative: Color,
    val teacherLevelNegative: Color,
    val teacherLevelMixed: Color,
    val teacherLevelPositive: Color,
    val teacherLevelVeryPositive: Color,
) {
    companion object {
        val Light = ExtendedColorTokens(
            lessonTypeLecture = Color(0xFF4984E2),
            lessonTypeLab = Color(0xFF8A5BE1),
            lessonTypePractice = Color(0xFFCDA634),
            lessonTypeAssessment = Color(0xFFF14F7B),
            lessonTypeConsultation = Color(0xFF355EDA),
            lessonTypeSport = Color(0xFF44C614),
            lessonTypeFree = Color(0xFF7A7A7A),
            lessonTypeDefault = Color(0xFF5C5C5C),
            recordbookPassed = Color(0xFF286B39),
            sportScoreAttendance = Color(0xFF345BD1),
            sportScoreBonus = Color(0xFFC93672),
            sportConditionAllowed = Color(0xFF216B3C),
            sportConditionWaiting = Color(0xFF285EA7),
            sportConditionWarning = Color(0xFF875200),
            sportConditionBlocked = Color(0xFFB32635),
            teacherLevelVeryNegative = Color(0xFFD32F2F),
            teacherLevelNegative = Color(0xFFE06C00),
            teacherLevelMixed = Color(0xFFB58900),
            teacherLevelPositive = Color(0xFF689F38),
            teacherLevelVeryPositive = Color(0xFF2E7D32),
        )

        /** The lesson types keep their light values: their night values come with the M3E tokens (M3-02). */
        val Dark = Light.copy(
            recordbookPassed = Color(0xFF89D995),
            sportScoreAttendance = Color(0xFF85A2FF),
            sportScoreBonus = Color(0xFFFF78AE),
            sportConditionAllowed = Color(0xFF86D9A3),
            sportConditionWaiting = Color(0xFF9BBEFF),
            sportConditionWarning = Color(0xFFF4C16F),
            sportConditionBlocked = Color(0xFFFFB3B9),
            teacherLevelVeryNegative = Color(0xFFFF6E6E),
            teacherLevelNegative = Color(0xFFFFA24C),
            teacherLevelMixed = Color(0xFFFFD54F),
            teacherLevelPositive = Color(0xFFAED581),
            teacherLevelVeryPositive = Color(0xFF4CAF50),
        )

        fun of(dark: Boolean): ExtendedColorTokens = if (dark) Dark else Light
    }
}

/**
 * [ExtendedColorTokens] as a screen uses them under a scheme. Sport condition accents keep their meaning in every
 * palette and get containers mixed 12 % over `surfaceContainerLowest`; teacher levels keep red to green but are
 * harmonized towards the primary colour, as the View screens do.
 */
@Immutable
data class ItmoExtendedColors(
    val lessonTypeLecture: Color,
    val lessonTypeLab: Color,
    val lessonTypePractice: Color,
    val lessonTypeAssessment: Color,
    val lessonTypeConsultation: Color,
    val lessonTypeSport: Color,
    val lessonTypeFree: Color,
    val lessonTypeDefault: Color,
    val recordbookPassed: Color,
    val sportScoreAttendance: Color,
    val sportScoreBonus: Color,
    val sportConditionAllowed: Color,
    val sportConditionAllowedContainer: Color,
    val sportConditionWaiting: Color,
    val sportConditionWaitingContainer: Color,
    val sportConditionWarning: Color,
    val sportConditionWarningContainer: Color,
    val sportConditionBlocked: Color,
    val sportConditionBlockedContainer: Color,
    val teacherLevelVeryNegative: Color,
    val teacherLevelNegative: Color,
    val teacherLevelMixed: Color,
    val teacherLevelPositive: Color,
    val teacherLevelVeryPositive: Color,
)

/** `ConditionTone.container`: the accent mixed 12 % over the lowest surface container. */
private const val CONDITION_CONTAINER_RATIO = 0.12f

internal fun ExtendedColorTokens.resolve(scheme: ColorScheme): ItmoExtendedColors {
    fun container(accent: Color) = blendArgb(scheme.surfaceContainerLowest, accent, CONDITION_CONTAINER_RATIO)
    fun level(tone: Color) = harmonize(tone, scheme.primary)
    return ItmoExtendedColors(
        lessonTypeLecture = lessonTypeLecture,
        lessonTypeLab = lessonTypeLab,
        lessonTypePractice = lessonTypePractice,
        lessonTypeAssessment = lessonTypeAssessment,
        lessonTypeConsultation = lessonTypeConsultation,
        lessonTypeSport = lessonTypeSport,
        lessonTypeFree = lessonTypeFree,
        lessonTypeDefault = lessonTypeDefault,
        recordbookPassed = recordbookPassed,
        sportScoreAttendance = sportScoreAttendance,
        sportScoreBonus = sportScoreBonus,
        sportConditionAllowed = sportConditionAllowed,
        sportConditionAllowedContainer = container(sportConditionAllowed),
        sportConditionWaiting = sportConditionWaiting,
        sportConditionWaitingContainer = container(sportConditionWaiting),
        sportConditionWarning = sportConditionWarning,
        sportConditionWarningContainer = container(sportConditionWarning),
        sportConditionBlocked = sportConditionBlocked,
        sportConditionBlockedContainer = container(sportConditionBlocked),
        teacherLevelVeryNegative = level(teacherLevelVeryNegative),
        teacherLevelNegative = level(teacherLevelNegative),
        teacherLevelMixed = level(teacherLevelMixed),
        teacherLevelPositive = level(teacherLevelPositive),
        teacherLevelVeryPositive = level(teacherLevelVeryPositive),
    )
}

internal val LocalItmoExtendedColors = staticCompositionLocalOf {
    ExtendedColorTokens.Light.resolve(staticColorScheme(dark = false))
}
