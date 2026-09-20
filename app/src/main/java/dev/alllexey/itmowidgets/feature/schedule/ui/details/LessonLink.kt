package dev.alllexey.itmowidgets.feature.schedule.ui.details

import java.net.URI

/** MyITMO calls the field `zoom_url`, but the link may point anywhere; the host says where. */
fun lessonLinkHost(url: String): String {
    val host = runCatching { URI(url.trim()).host }.getOrNull()?.removePrefix("www.")
    return host?.takeIf { it.isNotBlank() } ?: url.trim()
}
