package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource

/** The Android id of [textResource] for Views; presentation uses `core.text.toUiText()`. */
@StringRes
fun AppError.messageRes(): Int = ExportedStringIds.string(textResource().key)
