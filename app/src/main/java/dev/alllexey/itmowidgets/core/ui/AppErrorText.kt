package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textKey

/** The Android id of [textKey] for Views; presentation uses `core.text.toUiText()`. */
@StringRes
fun AppError.messageRes(): Int = ExportedStringIds.string(textKey())
