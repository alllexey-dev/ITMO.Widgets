package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/** The one message of each [AppError]; `core/ui/AppErrorText.kt` derives the Android id from the same key. */
fun AppError.textKey(): String = when (this) {
    AppError.Network -> CoreStrings.common_error_network
    AppError.Unauthorized -> CoreStrings.common_error_unauthorized
    AppError.Restricted -> CoreStrings.common_error_restricted
    AppError.Forbidden -> CoreStrings.common_error_forbidden
    AppError.NotFound -> CoreStrings.common_error_not_found
    AppError.CustomServicesDisabled -> CoreStrings.common_error_services_disabled
    AppError.DemoUnavailable -> CoreStrings.error_demo_unavailable
    is AppError.Unknown -> CoreStrings.common_error_unknown
}

fun AppError.toUiText(): UiText = UiText.Res(textKey())
