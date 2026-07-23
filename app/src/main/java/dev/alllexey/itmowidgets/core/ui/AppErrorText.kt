package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError

@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.common_error_network
    AppError.Unauthorized -> R.string.common_error_unauthorized
    AppError.Forbidden -> R.string.common_error_forbidden
    AppError.NotFound -> R.string.common_error_not_found
    is AppError.Unknown -> R.string.common_error_unknown
}
