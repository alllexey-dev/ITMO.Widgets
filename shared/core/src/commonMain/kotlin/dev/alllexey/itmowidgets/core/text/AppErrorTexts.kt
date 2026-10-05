package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.common_error_forbidden
import dev.alllexey.itmowidgets.shared.core.common_error_network
import dev.alllexey.itmowidgets.shared.core.common_error_not_found
import dev.alllexey.itmowidgets.shared.core.common_error_restricted
import dev.alllexey.itmowidgets.shared.core.common_error_services_disabled
import dev.alllexey.itmowidgets.shared.core.common_error_unauthorized
import dev.alllexey.itmowidgets.shared.core.common_error_unknown
import dev.alllexey.itmowidgets.shared.core.error_demo_unavailable
import org.jetbrains.compose.resources.StringResource

/** The one message of each [AppError]; `core/ui/AppErrorText.kt` derives the Android id from the same key. */
fun AppError.textResource(): StringResource = when (this) {
    AppError.Network -> Res.string.common_error_network
    AppError.Unauthorized -> Res.string.common_error_unauthorized
    AppError.Restricted -> Res.string.common_error_restricted
    AppError.Forbidden -> Res.string.common_error_forbidden
    AppError.NotFound -> Res.string.common_error_not_found
    AppError.CustomServicesDisabled -> Res.string.common_error_services_disabled
    AppError.DemoUnavailable -> Res.string.error_demo_unavailable
    is AppError.Unknown -> Res.string.common_error_unknown
}

fun AppError.toUiText(): UiText = UiText.Res(textResource())
