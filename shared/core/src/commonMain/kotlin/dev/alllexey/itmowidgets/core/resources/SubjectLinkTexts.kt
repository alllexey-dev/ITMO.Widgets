package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.url.StrictUri
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.links_category_chat
import dev.alllexey.itmowidgets.shared.core.links_category_exam
import dev.alllexey.itmowidgets.shared.core.links_category_materials
import dev.alllexey.itmowidgets.shared.core.links_category_notes
import dev.alllexey.itmowidgets.shared.core.links_category_other
import dev.alllexey.itmowidgets.shared.core.links_category_queue
import dev.alllexey.itmowidgets.shared.core.links_category_recordings
import dev.alllexey.itmowidgets.shared.core.links_category_scores
import dev.alllexey.itmowidgets.shared.core.links_category_tasks
import dev.alllexey.itmowidgets.shared.core.links_visibility_all
import dev.alllexey.itmowidgets.shared.core.links_visibility_flow_unnamed
import dev.alllexey.itmowidgets.shared.core.links_visibility_private

/** The site without `www.`, or the raw text, untrimmed, when it is not a URI with a server host. */
fun SubjectLink.host(): String = serverHost(url) ?: url

fun SubjectLink.displayTitle(): String = title ?: host()

fun LinkCategory.title(): UiText = UiText.Res(
    when (this) {
        LinkCategory.SCORES -> Res.string.links_category_scores
        LinkCategory.QUEUE -> Res.string.links_category_queue
        LinkCategory.MATERIALS -> Res.string.links_category_materials
        LinkCategory.TASKS -> Res.string.links_category_tasks
        LinkCategory.RECORDINGS -> Res.string.links_category_recordings
        LinkCategory.NOTES -> Res.string.links_category_notes
        LinkCategory.EXAM -> Res.string.links_category_exam
        LinkCategory.CHAT -> Res.string.links_category_chat
        LinkCategory.OTHER -> Res.string.links_category_other
    }
)

/** The same symbol marks the category in chips, sections and the editor. */
fun LinkCategory.icon(): AppIcon = when (this) {
    LinkCategory.SCORES -> AppIcon.TABLE
    LinkCategory.QUEUE -> AppIcon.FORMAT_LIST_NUMBERED
    LinkCategory.MATERIALS -> AppIcon.FOLDER
    LinkCategory.TASKS -> AppIcon.ASSIGNMENT
    LinkCategory.RECORDINGS -> AppIcon.VIDEOCAM
    LinkCategory.NOTES -> AppIcon.EDIT_NOTE
    LinkCategory.EXAM -> AppIcon.SCHOOL
    LinkCategory.CHAT -> AppIcon.CHAT
    LinkCategory.OTHER -> AppIcon.LINK
}

/** Chats show the messenger they lead to; everything else keeps its category symbol. */
fun linkIcon(category: LinkCategory, url: String): AppIcon {
    if (category != LinkCategory.CHAT) return category.icon()
    return when (serverHost(url.trim())) {
        "t.me", "telegram.me", "telegram.dog" -> AppIcon.BRAND_TELEGRAM
        "vk.com", "vk.ru", "vk.me", "m.vk.com" -> AppIcon.BRAND_VK
        else -> category.icon()
    }
}

/** A FLOW link is named by its schedule flow ([audienceLabel], e.g. «ФИЗ ПИИКТ 3.2.1»). */
fun LinkVisibility.label(audienceLabel: String? = null): UiText = when (this) {
    LinkVisibility.PRIVATE -> UiText.Res(Res.string.links_visibility_private)
    LinkVisibility.FLOW -> audienceLabel?.let(UiText::Dynamic) ?: UiText.Res(Res.string.links_visibility_flow_unnamed)
    LinkVisibility.ALL -> UiText.Res(Res.string.links_visibility_all)
}

/** Lower-cased without a locale, as the JVM `URI` host was read before. */
private fun serverHost(url: String): String? = StrictUri.parse(url)?.host?.lowercase()?.removePrefix("www.")
