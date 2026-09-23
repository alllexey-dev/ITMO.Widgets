package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.common_error_network
    AppError.Unauthorized -> R.string.common_error_unauthorized
    AppError.Restricted -> R.string.common_error_restricted
    AppError.Forbidden -> R.string.common_error_forbidden
    AppError.NotFound -> R.string.common_error_not_found
    AppError.CustomServicesDisabled -> R.string.common_error_services_disabled
    is AppError.Unknown -> R.string.common_error_unknown
}

fun AppError.toUiText(): UiText = UiText.Resource(messageRes())
