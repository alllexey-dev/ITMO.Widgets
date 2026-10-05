package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError

/** The BARS sign-in page: the callback is being exchanged, or the last attempt failed with [error]. */
data class BarsLoginUiState(val completing: Boolean = false, val error: AppError? = null)
