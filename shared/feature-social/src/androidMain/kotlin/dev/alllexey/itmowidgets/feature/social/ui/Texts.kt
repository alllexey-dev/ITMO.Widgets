package dev.alllexey.itmowidgets.feature.social.ui

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString

/** A catalog text by its key (`SocialStrings`, `CoreStrings`). */
@Composable
internal fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()
