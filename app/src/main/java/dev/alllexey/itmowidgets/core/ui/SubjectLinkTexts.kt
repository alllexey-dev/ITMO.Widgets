package dev.alllexey.itmowidgets.core.ui

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.text.UiText

fun LinkCategory.title(): UiText = UiText.Resource(when (this) {
    LinkCategory.SCORES -> R.string.links_category_scores
    LinkCategory.QUEUE -> R.string.links_category_queue
    LinkCategory.MATERIALS -> R.string.links_category_materials
    LinkCategory.TASKS -> R.string.links_category_tasks
    LinkCategory.RECORDINGS -> R.string.links_category_recordings
    LinkCategory.NOTES -> R.string.links_category_notes
    LinkCategory.EXAM -> R.string.links_category_exam
    LinkCategory.CHAT -> R.string.links_category_chat
    LinkCategory.OTHER -> R.string.links_category_other
})

/** A Material Symbols drawable; the same icon marks the category in chips, sections and the editor. */
fun LinkCategory.iconRes(): Int = when (this) {
    LinkCategory.SCORES -> R.drawable.ic_table
    LinkCategory.QUEUE -> R.drawable.ic_format_list_numbered
    LinkCategory.MATERIALS -> R.drawable.ic_folder
    LinkCategory.TASKS -> R.drawable.ic_assignment
    LinkCategory.RECORDINGS -> R.drawable.ic_videocam
    LinkCategory.NOTES -> R.drawable.ic_edit_note
    LinkCategory.EXAM -> R.drawable.ic_school
    LinkCategory.CHAT -> R.drawable.ic_chat
    LinkCategory.OTHER -> R.drawable.ic_link
}

/** Chats show the messenger they lead to; everything else keeps its category symbol. */
fun linkIconRes(category: LinkCategory, url: String): Int {
    if (category != LinkCategory.CHAT) return category.iconRes()
    val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")
    return when (host) {
        "t.me", "telegram.me", "telegram.dog" -> R.drawable.ic_brand_telegram
        "vk.com", "vk.ru", "vk.me", "m.vk.com" -> R.drawable.ic_brand_vk
        else -> category.iconRes()
    }
}

/** A FLOW link is named by its schedule flow ([audienceLabel], e.g. «ФИЗ ПИИКТ 3.2.1»). */
fun LinkVisibility.label(audienceLabel: String? = null): UiText = when (this) {
    LinkVisibility.PRIVATE -> UiText.Resource(R.string.links_visibility_private)
    LinkVisibility.FLOW -> audienceLabel?.let(UiText::Dynamic) ?: UiText.Resource(R.string.links_visibility_flow_unnamed)
    LinkVisibility.ALL -> UiText.Resource(R.string.links_visibility_all)
}
