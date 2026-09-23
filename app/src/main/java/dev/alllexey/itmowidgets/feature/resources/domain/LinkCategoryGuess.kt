package dev.alllexey.itmowidgets.feature.resources.domain

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import java.net.URI
import java.util.Locale

/** The category a pasted link most likely has, by its site; null when the site says nothing. */
fun guessCategory(url: String): LinkCategory? {
    val text = url.trim().let { if ("://" in it) it else "https://$it" }
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    val path = uri.path.orEmpty()
    fun on(domain: String) = host == domain || host.endsWith(".$domain")
    return when {
        host == "docs.google.com" && path.startsWith("/spreadsheets") -> LinkCategory.SCORES
        host == "docs.google.com" && path.startsWith("/forms") -> LinkCategory.QUEUE
        on("github.com") -> LinkCategory.TASKS
        on("youtube.com") || on("youtu.be") || on("vkvideo.ru") -> LinkCategory.RECORDINGS
        on("vk.com") && path.startsWith("/video") -> LinkCategory.RECORDINGS
        on("notion.so") || host.endsWith(".notion.site") -> LinkCategory.NOTES
        on("lms.itmo.ru") -> LinkCategory.MATERIALS
        on("t.me") || on("vk.me") || host == "chat.whatsapp.com" -> LinkCategory.CHAT
        else -> null
    }
}
