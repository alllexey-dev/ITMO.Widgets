package dev.alllexey.itmowidgets.core.text

/**
 * The app's UI language, sent to Backend as `Accept-Language` so its texts (scope titles) match the screens. The UI is
 * Russian only (ADR 0028); a translated UI would read it from the platform's chosen localization instead.
 */
const val APP_LANGUAGE = "ru"
