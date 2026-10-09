// Generated from shared/designsystem/tokens/itmo-tokens.json by scripts/ios/gen-tokens.py; do not edit.
// Regenerate with `python3 scripts/ios/gen-tokens.py`; scripts/ios/check-tokens.sh fails while this file is stale.

import SwiftUI

/// The token schema this file was generated from.
let itmoTokensSchemaVersion = 1

/// The static M3 colour roles, light and dark; `ItmoColor` picks one by the colour scheme.
struct ItmoColorRoles: Sendable {
    let primary: UInt32
    let onPrimary: UInt32
    let primaryContainer: UInt32
    let onPrimaryContainer: UInt32
    let inversePrimary: UInt32
    let secondary: UInt32
    let onSecondary: UInt32
    let secondaryContainer: UInt32
    let onSecondaryContainer: UInt32
    let tertiary: UInt32
    let onTertiary: UInt32
    let tertiaryContainer: UInt32
    let onTertiaryContainer: UInt32
    let background: UInt32
    let onBackground: UInt32
    let surface: UInt32
    let onSurface: UInt32
    let surfaceVariant: UInt32
    let onSurfaceVariant: UInt32
    let surfaceTint: UInt32
    let inverseSurface: UInt32
    let inverseOnSurface: UInt32
    let error: UInt32
    let onError: UInt32
    let errorContainer: UInt32
    let onErrorContainer: UInt32
    let outline: UInt32
    let outlineVariant: UInt32
    let scrim: UInt32
    let surfaceBright: UInt32
    let surfaceDim: UInt32
    let surfaceContainerLowest: UInt32
    let surfaceContainerLow: UInt32
    let surfaceContainer: UInt32
    let surfaceContainerHigh: UInt32
    let surfaceContainerHighest: UInt32
    let primaryFixed: UInt32
    let primaryFixedDim: UInt32
    let onPrimaryFixed: UInt32
    let onPrimaryFixedVariant: UInt32
    let secondaryFixed: UInt32
    let secondaryFixedDim: UInt32
    let onSecondaryFixed: UInt32
    let onSecondaryFixedVariant: UInt32
    let tertiaryFixed: UInt32
    let tertiaryFixedDim: UInt32
    let onTertiaryFixed: UInt32
    let onTertiaryFixedVariant: UInt32
}

extension ItmoColorRoles {
    static let light = ItmoColorRoles(
        primary: 0x425E91,
        onPrimary: 0xFFFFFF,
        primaryContainer: 0xD7E2FF,
        onPrimaryContainer: 0x294677,
        inversePrimary: 0xABC7FF,
        secondary: 0x565E71,
        onSecondary: 0xFFFFFF,
        secondaryContainer: 0xDAE2F9,
        onSecondaryContainer: 0x3E4759,
        tertiary: 0x705574,
        onTertiary: 0xFFFFFF,
        tertiaryContainer: 0xFAD8FD,
        onTertiaryContainer: 0x573E5B,
        background: 0xF9F9FF,
        onBackground: 0x1A1C20,
        surface: 0xF9F9FF,
        onSurface: 0x1A1C20,
        surfaceVariant: 0xE0E2EC,
        onSurfaceVariant: 0x44474E,
        surfaceTint: 0x425E91,
        inverseSurface: 0x2E3036,
        inverseOnSurface: 0xF0F0F7,
        error: 0xBA1A1A,
        onError: 0xFFFFFF,
        errorContainer: 0xFFDAD6,
        onErrorContainer: 0x93000A,
        outline: 0x74777F,
        outlineVariant: 0xC4C6D0,
        scrim: 0x000000,
        surfaceBright: 0xF9F9FF,
        surfaceDim: 0xD9D9E0,
        surfaceContainerLowest: 0xFFFFFF,
        surfaceContainerLow: 0xF3F3FA,
        surfaceContainer: 0xEDEDF4,
        surfaceContainerHigh: 0xE8E7EE,
        surfaceContainerHighest: 0xE2E2E9,
        primaryFixed: 0xD7E2FF,
        primaryFixedDim: 0xABC7FF,
        onPrimaryFixed: 0x001B3F,
        onPrimaryFixedVariant: 0x294677,
        secondaryFixed: 0xDAE2F9,
        secondaryFixedDim: 0xBEC6DC,
        onSecondaryFixed: 0x131C2C,
        onSecondaryFixedVariant: 0x3E4759,
        tertiaryFixed: 0xFAD8FD,
        tertiaryFixedDim: 0xDDBCE0,
        onTertiaryFixed: 0x29132E,
        onTertiaryFixedVariant: 0x573E5B
    )
    static let dark = ItmoColorRoles(
        primary: 0xABC7FF,
        onPrimary: 0x0D2F5F,
        primaryContainer: 0x294677,
        onPrimaryContainer: 0xD7E2FF,
        inversePrimary: 0x425E91,
        secondary: 0xBEC6DC,
        onSecondary: 0x283041,
        secondaryContainer: 0x3E4759,
        onSecondaryContainer: 0xDAE2F9,
        tertiary: 0xDDBCE0,
        onTertiary: 0x3F2844,
        tertiaryContainer: 0x573E5B,
        onTertiaryContainer: 0xFAD8FD,
        background: 0x111318,
        onBackground: 0xE2E2E9,
        surface: 0x111318,
        onSurface: 0xE2E2E9,
        surfaceVariant: 0x44474E,
        onSurfaceVariant: 0xC4C6D0,
        surfaceTint: 0xABC7FF,
        inverseSurface: 0xE2E2E9,
        inverseOnSurface: 0x2E3036,
        error: 0xFFB4AB,
        onError: 0x690005,
        errorContainer: 0x93000A,
        onErrorContainer: 0xFFDAD6,
        outline: 0x8E9099,
        outlineVariant: 0x44474E,
        scrim: 0x000000,
        surfaceBright: 0x37393E,
        surfaceDim: 0x111318,
        surfaceContainerLowest: 0x0C0E13,
        surfaceContainerLow: 0x1A1C20,
        surfaceContainer: 0x1E2025,
        surfaceContainerHigh: 0x282A2F,
        surfaceContainerHighest: 0x33353A,
        primaryFixed: 0xD7E2FF,
        primaryFixedDim: 0xABC7FF,
        onPrimaryFixed: 0x001B3F,
        onPrimaryFixedVariant: 0x294677,
        secondaryFixed: 0xDAE2F9,
        secondaryFixedDim: 0xBEC6DC,
        onSecondaryFixed: 0x131C2C,
        onSecondaryFixedVariant: 0x3E4759,
        tertiaryFixed: 0xFAD8FD,
        tertiaryFixedDim: 0xDDBCE0,
        onTertiaryFixed: 0x29132E,
        onTertiaryFixedVariant: 0x573E5B
    )
}

/// The app's own colours beside the scheme, light and dark.
struct ItmoExtendedColorRoles: Sendable {
    let lessonTypeLecture: UInt32
    let lessonTypeLab: UInt32
    let lessonTypePractice: UInt32
    let lessonTypeAssessment: UInt32
    let lessonTypeConsultation: UInt32
    let lessonTypeSport: UInt32
    let lessonTypeFree: UInt32
    let lessonTypeDefault: UInt32
    let recordbookPassed: UInt32
    let sportScoreAttendance: UInt32
    let sportScoreBonus: UInt32
    let sportConditionAllowed: UInt32
    let sportConditionWaiting: UInt32
    let sportConditionWarning: UInt32
    let sportConditionBlocked: UInt32
    let teacherLevelVeryNegative: UInt32
    let teacherLevelNegative: UInt32
    let teacherLevelMixed: UInt32
    let teacherLevelPositive: UInt32
    let teacherLevelVeryPositive: UInt32
}

extension ItmoExtendedColorRoles {
    static let light = ItmoExtendedColorRoles(
        lessonTypeLecture: 0x4984E2,
        lessonTypeLab: 0x8A5BE1,
        lessonTypePractice: 0xCDA634,
        lessonTypeAssessment: 0xF14F7B,
        lessonTypeConsultation: 0x355EDA,
        lessonTypeSport: 0x44C614,
        lessonTypeFree: 0x7A7A7A,
        lessonTypeDefault: 0x5C5C5C,
        recordbookPassed: 0x286B39,
        sportScoreAttendance: 0x345BD1,
        sportScoreBonus: 0xC93672,
        sportConditionAllowed: 0x216B3C,
        sportConditionWaiting: 0x285EA7,
        sportConditionWarning: 0x875200,
        sportConditionBlocked: 0xB32635,
        teacherLevelVeryNegative: 0xD32F2F,
        teacherLevelNegative: 0xE06C00,
        teacherLevelMixed: 0xB58900,
        teacherLevelPositive: 0x689F38,
        teacherLevelVeryPositive: 0x2E7D32
    )
    static let dark = ItmoExtendedColorRoles(
        lessonTypeLecture: 0x4984E2,
        lessonTypeLab: 0x8A5BE1,
        lessonTypePractice: 0xCDA634,
        lessonTypeAssessment: 0xF14F7B,
        lessonTypeConsultation: 0x355EDA,
        lessonTypeSport: 0x44C614,
        lessonTypeFree: 0x7A7A7A,
        lessonTypeDefault: 0x5C5C5C,
        recordbookPassed: 0x89D995,
        sportScoreAttendance: 0x85A2FF,
        sportScoreBonus: 0xFF78AE,
        sportConditionAllowed: 0x86D9A3,
        sportConditionWaiting: 0x9BBEFF,
        sportConditionWarning: 0xF4C16F,
        sportConditionBlocked: 0xFFB3B9,
        teacherLevelVeryNegative: 0xFF6E6E,
        teacherLevelNegative: 0xFFA24C,
        teacherLevelMixed: 0xFFD54F,
        teacherLevelPositive: 0xAED581,
        teacherLevelVeryPositive: 0x4CAF50
    )
}

/// Every role as a `Color` that follows the colour scheme of its environment.
enum ItmoColor {
    static let primary = Color(itmo: \ItmoColorRoles.primary)
    static let onPrimary = Color(itmo: \ItmoColorRoles.onPrimary)
    static let primaryContainer = Color(itmo: \ItmoColorRoles.primaryContainer)
    static let onPrimaryContainer = Color(itmo: \ItmoColorRoles.onPrimaryContainer)
    static let inversePrimary = Color(itmo: \ItmoColorRoles.inversePrimary)
    static let secondary = Color(itmo: \ItmoColorRoles.secondary)
    static let onSecondary = Color(itmo: \ItmoColorRoles.onSecondary)
    static let secondaryContainer = Color(itmo: \ItmoColorRoles.secondaryContainer)
    static let onSecondaryContainer = Color(itmo: \ItmoColorRoles.onSecondaryContainer)
    static let tertiary = Color(itmo: \ItmoColorRoles.tertiary)
    static let onTertiary = Color(itmo: \ItmoColorRoles.onTertiary)
    static let tertiaryContainer = Color(itmo: \ItmoColorRoles.tertiaryContainer)
    static let onTertiaryContainer = Color(itmo: \ItmoColorRoles.onTertiaryContainer)
    static let background = Color(itmo: \ItmoColorRoles.background)
    static let onBackground = Color(itmo: \ItmoColorRoles.onBackground)
    static let surface = Color(itmo: \ItmoColorRoles.surface)
    static let onSurface = Color(itmo: \ItmoColorRoles.onSurface)
    static let surfaceVariant = Color(itmo: \ItmoColorRoles.surfaceVariant)
    static let onSurfaceVariant = Color(itmo: \ItmoColorRoles.onSurfaceVariant)
    static let surfaceTint = Color(itmo: \ItmoColorRoles.surfaceTint)
    static let inverseSurface = Color(itmo: \ItmoColorRoles.inverseSurface)
    static let inverseOnSurface = Color(itmo: \ItmoColorRoles.inverseOnSurface)
    static let error = Color(itmo: \ItmoColorRoles.error)
    static let onError = Color(itmo: \ItmoColorRoles.onError)
    static let errorContainer = Color(itmo: \ItmoColorRoles.errorContainer)
    static let onErrorContainer = Color(itmo: \ItmoColorRoles.onErrorContainer)
    static let outline = Color(itmo: \ItmoColorRoles.outline)
    static let outlineVariant = Color(itmo: \ItmoColorRoles.outlineVariant)
    static let scrim = Color(itmo: \ItmoColorRoles.scrim)
    static let surfaceBright = Color(itmo: \ItmoColorRoles.surfaceBright)
    static let surfaceDim = Color(itmo: \ItmoColorRoles.surfaceDim)
    static let surfaceContainerLowest = Color(itmo: \ItmoColorRoles.surfaceContainerLowest)
    static let surfaceContainerLow = Color(itmo: \ItmoColorRoles.surfaceContainerLow)
    static let surfaceContainer = Color(itmo: \ItmoColorRoles.surfaceContainer)
    static let surfaceContainerHigh = Color(itmo: \ItmoColorRoles.surfaceContainerHigh)
    static let surfaceContainerHighest = Color(itmo: \ItmoColorRoles.surfaceContainerHighest)
    static let primaryFixed = Color(itmo: \ItmoColorRoles.primaryFixed)
    static let primaryFixedDim = Color(itmo: \ItmoColorRoles.primaryFixedDim)
    static let onPrimaryFixed = Color(itmo: \ItmoColorRoles.onPrimaryFixed)
    static let onPrimaryFixedVariant = Color(itmo: \ItmoColorRoles.onPrimaryFixedVariant)
    static let secondaryFixed = Color(itmo: \ItmoColorRoles.secondaryFixed)
    static let secondaryFixedDim = Color(itmo: \ItmoColorRoles.secondaryFixedDim)
    static let onSecondaryFixed = Color(itmo: \ItmoColorRoles.onSecondaryFixed)
    static let onSecondaryFixedVariant = Color(itmo: \ItmoColorRoles.onSecondaryFixedVariant)
    static let tertiaryFixed = Color(itmo: \ItmoColorRoles.tertiaryFixed)
    static let tertiaryFixedDim = Color(itmo: \ItmoColorRoles.tertiaryFixedDim)
    static let onTertiaryFixed = Color(itmo: \ItmoColorRoles.onTertiaryFixed)
    static let onTertiaryFixedVariant = Color(itmo: \ItmoColorRoles.onTertiaryFixedVariant)
    static let lessonTypeLecture = Color(itmo: \ItmoExtendedColorRoles.lessonTypeLecture)
    static let lessonTypeLab = Color(itmo: \ItmoExtendedColorRoles.lessonTypeLab)
    static let lessonTypePractice = Color(itmo: \ItmoExtendedColorRoles.lessonTypePractice)
    static let lessonTypeAssessment = Color(itmo: \ItmoExtendedColorRoles.lessonTypeAssessment)
    static let lessonTypeConsultation = Color(itmo: \ItmoExtendedColorRoles.lessonTypeConsultation)
    static let lessonTypeSport = Color(itmo: \ItmoExtendedColorRoles.lessonTypeSport)
    static let lessonTypeFree = Color(itmo: \ItmoExtendedColorRoles.lessonTypeFree)
    static let lessonTypeDefault = Color(itmo: \ItmoExtendedColorRoles.lessonTypeDefault)
    static let recordbookPassed = Color(itmo: \ItmoExtendedColorRoles.recordbookPassed)
    static let sportScoreAttendance = Color(itmo: \ItmoExtendedColorRoles.sportScoreAttendance)
    static let sportScoreBonus = Color(itmo: \ItmoExtendedColorRoles.sportScoreBonus)
    static let sportConditionAllowed = Color(itmo: \ItmoExtendedColorRoles.sportConditionAllowed)
    static let sportConditionWaiting = Color(itmo: \ItmoExtendedColorRoles.sportConditionWaiting)
    static let sportConditionWarning = Color(itmo: \ItmoExtendedColorRoles.sportConditionWarning)
    static let sportConditionBlocked = Color(itmo: \ItmoExtendedColorRoles.sportConditionBlocked)
    static let teacherLevelVeryNegative = Color(itmo: \ItmoExtendedColorRoles.teacherLevelVeryNegative)
    static let teacherLevelNegative = Color(itmo: \ItmoExtendedColorRoles.teacherLevelNegative)
    static let teacherLevelMixed = Color(itmo: \ItmoExtendedColorRoles.teacherLevelMixed)
    static let teacherLevelPositive = Color(itmo: \ItmoExtendedColorRoles.teacherLevelPositive)
    static let teacherLevelVeryPositive = Color(itmo: \ItmoExtendedColorRoles.teacherLevelVeryPositive)
}

/// The corner scale in points; `full` is a `Capsule`.
enum ItmoCorner {
    static let extraSmall: CGFloat = 4
    static let small: CGFloat = 8
    static let medium: CGFloat = 12
    static let large: CGFloat = 16
    static let largeIncreased: CGFloat = 20
    static let extraLarge: CGFloat = 28
    static let extraLargeIncreased: CGFloat = 32
    static let extraExtraLarge: CGFloat = 48
}

/// The named card and group shapes in points.
enum ItmoShapes {
    static let cardContent: CGFloat = 20
    static let cardSummary: CGFloat = 28
    static let cardHero: CGFloat = 28
    static let scheduleDay: CGFloat = 16
    static let groupOuter: CGFloat = 20
    static let groupInner: CGFloat = 4
    static let groupGap: CGFloat = 2
    static let cardStroke: CGFloat = 1
    static let cardElevation: CGFloat = 0
}

/// Spacing in points.
enum ItmoSpacing {
    static let related: CGFloat = 4
    static let compact: CGFloat = 8
    static let content: CGFloat = 12
    static let group: CGFloat = 16
    static let section: CGFloat = 24
    static let screenMargin: CGFloat = 16
    static let cardPadding: CGFloat = 16
    static let summaryPadding: CGFloat = 20
    static let touchTarget: CGFloat = 48
    static let fabStackClearance: CGFloat = 152
    static let statePadding: CGFloat = 32
    static let stateIcon: CGFloat = 64
    static let stateInlineIcon: CGFloat = 56
}

/// The M3 type roles. Each is a Dynamic Type text style with the role's weight, never a fixed size.
enum ItmoTypeRole: CaseIterable, Sendable {
    case displayLarge
    case displayMedium
    case displaySmall
    case headlineLarge
    case headlineMedium
    case headlineSmall
    case titleLarge
    case titleMedium
    case titleSmall
    case bodyLarge
    case bodyMedium
    case bodySmall
    case labelLarge
    case labelMedium
    case labelSmall

    var textStyle: Font.TextStyle {
        switch self {
        case .displayLarge: .largeTitle
        case .displayMedium: .largeTitle
        case .displaySmall: .largeTitle
        case .headlineLarge: .largeTitle
        case .headlineMedium: .title
        case .headlineSmall: .title2
        case .titleLarge: .title2
        case .titleMedium: .callout
        case .titleSmall: .subheadline
        case .bodyLarge: .body
        case .bodyMedium: .subheadline
        case .bodySmall: .footnote
        case .labelLarge: .subheadline
        case .labelMedium: .caption
        case .labelSmall: .caption2
        }
    }

    var weight: Font.Weight {
        switch self {
        case .displayLarge: .regular
        case .displayMedium: .regular
        case .displaySmall: .regular
        case .headlineLarge: .regular
        case .headlineMedium: .regular
        case .headlineSmall: .regular
        case .titleLarge: .regular
        case .titleMedium: .medium
        case .titleSmall: .medium
        case .bodyLarge: .regular
        case .bodyMedium: .regular
        case .bodySmall: .regular
        case .labelLarge: .medium
        case .labelMedium: .medium
        case .labelSmall: .medium
        }
    }

    var emphasizedWeight: Font.Weight {
        switch self {
        case .displayLarge: .medium
        case .displayMedium: .medium
        case .displaySmall: .medium
        case .headlineLarge: .medium
        case .headlineMedium: .medium
        case .headlineSmall: .medium
        case .titleLarge: .medium
        case .titleMedium: .bold
        case .titleSmall: .bold
        case .bodyLarge: .medium
        case .bodyMedium: .medium
        case .bodySmall: .medium
        case .labelLarge: .bold
        case .labelMedium: .bold
        case .labelSmall: .bold
        }
    }
}

/// Durations in seconds, the easing of the kit animations and the skeleton pulse.
enum ItmoMotion {
    static let quick: TimeInterval = 0.18
    static let standard: TimeInterval = 0.22
    static let emphasis: TimeInterval = 0.26
    static let reveal: TimeInterval = 0.3
    static let progress: TimeInterval = 0.7
    static let progressSlow: TimeInterval = 1
    static let pulse: TimeInterval = 1.2
    static let pulseMinAlpha: Double = 0.55

    /// The cubic Bezier easing `(x1, y1, x2, y2)` of every kit animation.
    static let easing: (x1: Double, y1: Double, x2: Double, y2: Double) = (0.2, 0, 0, 1)
}
