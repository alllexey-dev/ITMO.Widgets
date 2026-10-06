package dev.alllexey.itmowidgets.feature.settings.presentation

/** The custom spoiler image of the QR widget: [configured] is null until the first disk read. */
data class CustomSpoilerUiState(val configured: Boolean? = null, val busy: Boolean = false)

enum class CustomSpoilerEvent { SAVED, RESET, FAILED }
