package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.theme.ExtendedColorTokens
import dev.alllexey.itmowidgets.designsystem.theme.staticColorScheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * `tokens/itmo-tokens.json`, the one token source of iOS (L18 `gen-tokens.py`) and Web (L22 `sync-tokens.sh`):
 * the static schemes by M3 role, the extended colours, shapes, spacing, type and motion. Colours are opaque
 * `#RRGGBB`, sizes dp or sp, durations ms. A breaking change to the shape bumps [SCHEMA_VERSION].
 */
internal fun designTokensJson(): String = PrettyJson.encodeToString(
    buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("source", "app")
        put("generatedBy", "shared/designsystem exportDesignTokens; do not edit")
        putJsonObject("color") {
            putJsonObject("scheme") {
                put("light", staticColorScheme(dark = false).roles())
                put("dark", staticColorScheme(dark = true).roles())
            }
            putJsonObject("extended") {
                put("light", ExtendedColorTokens.Light.slots())
                put("dark", ExtendedColorTokens.Dark.slots())
            }
            putJsonObject("derived") {
                put("sportConditionContainer", "the accent mixed 12 % (sRGB) over surfaceContainerLowest")
                put("teacherLevel", "harmonized towards primary (hue up to 15 degrees)")
            }
        }
        putJsonObject("shape") {
            putJsonObject("corner") {
                dp("extraSmall", ShapeTokens.ExtraSmall)
                dp("small", ShapeTokens.Small)
                dp("medium", ShapeTokens.Medium)
                dp("large", ShapeTokens.Large)
                dp("largeIncreased", ShapeTokens.LargeIncreased)
                dp("extraLarge", ShapeTokens.ExtraLarge)
                dp("extraLargeIncreased", ShapeTokens.ExtraLargeIncreased)
                dp("extraExtraLarge", ShapeTokens.ExtraExtraLarge)
                put("full", "50%")
            }
            putJsonObject("named") {
                dp("cardContent", ShapeTokens.CardContent)
                dp("cardSummary", ShapeTokens.CardSummary)
                dp("cardHero", ShapeTokens.CardHero)
                dp("scheduleDay", ShapeTokens.ScheduleDay)
                dp("groupOuter", ShapeTokens.GroupOuter)
                dp("groupInner", ShapeTokens.GroupInner)
                dp("groupGap", ShapeTokens.GroupGap)
                dp("cardStroke", ShapeTokens.CardStroke)
                dp("cardElevation", ShapeTokens.CardElevation)
            }
        }
        putJsonObject("spacing") {
            with(ItmoSpacing.Default) {
                dp("related", related)
                dp("compact", compact)
                dp("content", content)
                dp("group", group)
                dp("section", section)
                dp("screenMargin", screenMargin)
                dp("cardPadding", cardPadding)
                dp("summaryPadding", summaryPadding)
                dp("touchTarget", touchTarget)
                dp("fabStackClearance", fabStackClearance)
                dp("statePadding", statePadding)
                dp("stateIcon", stateIcon)
                dp("stateInlineIcon", stateInlineIcon)
            }
        }
        putJsonObject("type") {
            put("fontFamily", "system")
            putJsonObject("roles") {
                TypeScaleTokens.roles.forEach { (name, role) -> put(name, role.first.json()) }
            }
            putJsonObject("emphasized") {
                TypeScaleTokens.roles.forEach { (name, role) -> put(name, role.second.json()) }
            }
        }
        putJsonObject("motion") {
            with(ItmoMotion.Default) {
                putJsonObject("durationMs") {
                    put("quick", quickMillis)
                    put("standard", standardMillis)
                    put("emphasis", emphasisMillis)
                    put("reveal", revealMillis)
                    put("progress", progressMillis)
                    put("progressSlow", progressSlowMillis)
                    put("pulse", pulseMillis)
                }
                put("pulseMinAlpha", pulseMinAlpha)
                putJsonArray("easing") { ItmoMotion.EasingControlPoints.forEach { add(it) } }
                put("scheme", "standard")
                put("reducedMotion", "end state without animation")
            }
        }
    },
) + "\n"

/** The version of the file's shape; consumers reject one they do not know. */
internal const val SCHEMA_VERSION = 1

private val PrettyJson = Json { prettyPrint = true }

private fun JsonObjectBuilder.dp(name: String, value: Dp) = put(name, value.value)

private fun TypeRole.json() = buildJsonObject {
    put("size", size)
    put("lineHeight", lineHeight)
    put("tracking", tracking)
    put("weight", weight)
}

private fun ColorScheme.roles() = buildJsonObject {
    ColorRoles.forEach { (name, role) -> put(name, role(this@roles).hex()) }
}

private fun ExtendedColorTokens.slots() = buildJsonObject {
    with(this@slots) {
        put("lessonTypeLecture", lessonTypeLecture.hex())
        put("lessonTypeLab", lessonTypeLab.hex())
        put("lessonTypePractice", lessonTypePractice.hex())
        put("lessonTypeAssessment", lessonTypeAssessment.hex())
        put("lessonTypeConsultation", lessonTypeConsultation.hex())
        put("lessonTypeSport", lessonTypeSport.hex())
        put("lessonTypeFree", lessonTypeFree.hex())
        put("lessonTypeDefault", lessonTypeDefault.hex())
        put("recordbookPassed", recordbookPassed.hex())
        put("sportScoreAttendance", sportScoreAttendance.hex())
        put("sportScoreBonus", sportScoreBonus.hex())
        put("sportConditionAllowed", sportConditionAllowed.hex())
        put("sportConditionWaiting", sportConditionWaiting.hex())
        put("sportConditionWarning", sportConditionWarning.hex())
        put("sportConditionBlocked", sportConditionBlocked.hex())
        put("teacherLevelVeryNegative", teacherLevelVeryNegative.hex())
        put("teacherLevelNegative", teacherLevelNegative.hex())
        put("teacherLevelMixed", teacherLevelMixed.hex())
        put("teacherLevelPositive", teacherLevelPositive.hex())
        put("teacherLevelVeryPositive", teacherLevelVeryPositive.hex())
    }
}

private fun Color.hex(): String {
    val argb = toArgb()
    check(argb ushr 24 == 0xFF) { "Token colours are opaque; ${argb.toUInt().toString(16)} is not" }
    return "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}

/** Every M3 colour role of the scheme, in the order of the M3 spec. */
private val ColorRoles: List<Pair<String, (ColorScheme) -> Color>> = listOf(
    "primary" to { it.primary },
    "onPrimary" to { it.onPrimary },
    "primaryContainer" to { it.primaryContainer },
    "onPrimaryContainer" to { it.onPrimaryContainer },
    "inversePrimary" to { it.inversePrimary },
    "secondary" to { it.secondary },
    "onSecondary" to { it.onSecondary },
    "secondaryContainer" to { it.secondaryContainer },
    "onSecondaryContainer" to { it.onSecondaryContainer },
    "tertiary" to { it.tertiary },
    "onTertiary" to { it.onTertiary },
    "tertiaryContainer" to { it.tertiaryContainer },
    "onTertiaryContainer" to { it.onTertiaryContainer },
    "background" to { it.background },
    "onBackground" to { it.onBackground },
    "surface" to { it.surface },
    "onSurface" to { it.onSurface },
    "surfaceVariant" to { it.surfaceVariant },
    "onSurfaceVariant" to { it.onSurfaceVariant },
    "surfaceTint" to { it.surfaceTint },
    "inverseSurface" to { it.inverseSurface },
    "inverseOnSurface" to { it.inverseOnSurface },
    "error" to { it.error },
    "onError" to { it.onError },
    "errorContainer" to { it.errorContainer },
    "onErrorContainer" to { it.onErrorContainer },
    "outline" to { it.outline },
    "outlineVariant" to { it.outlineVariant },
    "scrim" to { it.scrim },
    "surfaceBright" to { it.surfaceBright },
    "surfaceDim" to { it.surfaceDim },
    "surfaceContainerLowest" to { it.surfaceContainerLowest },
    "surfaceContainerLow" to { it.surfaceContainerLow },
    "surfaceContainer" to { it.surfaceContainer },
    "surfaceContainerHigh" to { it.surfaceContainerHigh },
    "surfaceContainerHighest" to { it.surfaceContainerHighest },
    "primaryFixed" to { it.primaryFixed },
    "primaryFixedDim" to { it.primaryFixedDim },
    "onPrimaryFixed" to { it.onPrimaryFixed },
    "onPrimaryFixedVariant" to { it.onPrimaryFixedVariant },
    "secondaryFixed" to { it.secondaryFixed },
    "secondaryFixedDim" to { it.secondaryFixedDim },
    "onSecondaryFixed" to { it.onSecondaryFixed },
    "onSecondaryFixedVariant" to { it.onSecondaryFixedVariant },
    "tertiaryFixed" to { it.tertiaryFixed },
    "tertiaryFixedDim" to { it.tertiaryFixedDim },
    "onTertiaryFixed" to { it.onTertiaryFixed },
    "onTertiaryFixedVariant" to { it.onTertiaryFixedVariant },
)
