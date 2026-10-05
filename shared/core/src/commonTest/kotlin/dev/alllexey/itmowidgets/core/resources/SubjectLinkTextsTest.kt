package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
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
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectLinkTextsTest {

    @Test
    fun everyCategoryHasItsTitle() {
        val titles = mapOf(
            LinkCategory.SCORES to Res.string.links_category_scores,
            LinkCategory.QUEUE to Res.string.links_category_queue,
            LinkCategory.MATERIALS to Res.string.links_category_materials,
            LinkCategory.TASKS to Res.string.links_category_tasks,
            LinkCategory.RECORDINGS to Res.string.links_category_recordings,
            LinkCategory.NOTES to Res.string.links_category_notes,
            LinkCategory.EXAM to Res.string.links_category_exam,
            LinkCategory.CHAT to Res.string.links_category_chat,
            LinkCategory.OTHER to Res.string.links_category_other,
        )

        assertEquals(LinkCategory.entries.toSet(), titles.keys)
        titles.forEach { (category, resource) -> assertEquals(UiText.Res(resource), category.title(), category.name) }
    }

    @Test
    fun everyCategoryHasItsSymbol() {
        val icons = mapOf(
            LinkCategory.SCORES to AppIcon.TABLE,
            LinkCategory.QUEUE to AppIcon.FORMAT_LIST_NUMBERED,
            LinkCategory.MATERIALS to AppIcon.FOLDER,
            LinkCategory.TASKS to AppIcon.ASSIGNMENT,
            LinkCategory.RECORDINGS to AppIcon.VIDEOCAM,
            LinkCategory.NOTES to AppIcon.EDIT_NOTE,
            LinkCategory.EXAM to AppIcon.SCHOOL,
            LinkCategory.CHAT to AppIcon.CHAT,
            LinkCategory.OTHER to AppIcon.LINK,
        )

        assertEquals(LinkCategory.entries.toSet(), icons.keys)
        icons.forEach { (category, icon) -> assertEquals(icon, category.icon(), category.name) }
    }

    @Test
    fun chatsShowTheMessengerTheyLeadTo() {
        val icons = mapOf(
            "https://t.me/+AbCd" to AppIcon.BRAND_TELEGRAM,
            "https://telegram.me/itmo" to AppIcon.BRAND_TELEGRAM,
            "https://telegram.dog/itmo" to AppIcon.BRAND_TELEGRAM,
            "https://www.t.me/itmo" to AppIcon.BRAND_TELEGRAM,
            "HTTPS://T.ME/Itmo" to AppIcon.BRAND_TELEGRAM,
            " https://telegram.me/itmo " to AppIcon.BRAND_TELEGRAM,
            "https://vk.com/im?sel=c1" to AppIcon.BRAND_VK,
            "https://vk.ru/club1" to AppIcon.BRAND_VK,
            "https://vk.me/join/abc" to AppIcon.BRAND_VK,
            "https://m.vk.com/club1" to AppIcon.BRAND_VK,
            "https://www.vk.com/im?sel=c1" to AppIcon.BRAND_VK,
            "https://VK.Com/club1" to AppIcon.BRAND_VK,
            "\thttps://vk.me/join/abc\n" to AppIcon.BRAND_VK,
            "https://chat.whatsapp.com/x" to AppIcon.CHAT,
            "https://web.telegram.org/a" to AppIcon.CHAT,
            "https://t.me.example.org/x" to AppIcon.CHAT,
            "tg://resolve?domain=itmo" to AppIcon.CHAT,
            "t.me/itmo" to AppIcon.CHAT,
            "https://t.me/a b" to AppIcon.CHAT,
            "не ссылка" to AppIcon.CHAT,
            "" to AppIcon.CHAT,
        )

        icons.forEach { (url, icon) -> assertEquals(icon, linkIcon(LinkCategory.CHAT, url), url) }
    }

    @Test
    fun otherCategoriesKeepTheirSymbolOnMessengerLinks() {
        LinkCategory.entries.filter { it != LinkCategory.CHAT }.forEach { category ->
            listOf("https://t.me/itmo", "https://vk.com/club1").forEach { url ->
                assertEquals(category.icon(), linkIcon(category, url), "$category $url")
            }
        }
    }

    @Test
    fun hostIsTheLowerCasedSiteWithoutWwwOrTheRawText() {
        val hosts = mapOf(
            "https://t.me/+AbCd" to "t.me",
            "https://www.vk.com/im?sel=c1" to "vk.com",
            "https://m.vk.com/club1" to "m.vk.com",
            "https://WWW.Example.ORG/Path" to "example.org",
            "http://user@lms.itmo.ru:8080/course?id=1#top" to "lms.itmo.ru",
            "https://192.168.0.1/x" to "192.168.0.1",
            " https://t.me/itmo " to " https://t.me/itmo ",
            "lms.itmo.ru/course" to "lms.itmo.ru/course",
            "mailto:dean@itmo.ru" to "mailto:dean@itmo.ru",
            "https://" to "https://",
            "https://t.me/a b" to "https://t.me/a b",
            "не ссылка" to "не ссылка",
        )

        hosts.forEach { (url, host) -> assertEquals(host, link(url).host(), url) }
    }

    @Test
    fun displayTitleIsTheTitleOrTheHost() {
        assertEquals("Таблица", link("https://www.example.org/x", title = "Таблица").displayTitle())
        assertEquals("example.org", link("https://www.example.org/x", title = null).displayTitle())
        assertEquals("не ссылка", link("не ссылка", title = null).displayTitle())
    }

    @Test
    fun aFlowLinkIsNamedByItsFlow() {
        assertEquals(UiText.Res(Res.string.links_visibility_private), LinkVisibility.PRIVATE.label("ФИЗ ПИИКТ 3.2.1"))
        assertEquals(UiText.Dynamic("ФИЗ ПИИКТ 3.2.1"), LinkVisibility.FLOW.label("ФИЗ ПИИКТ 3.2.1"))
        assertEquals(UiText.Res(Res.string.links_visibility_flow_unnamed), LinkVisibility.FLOW.label())
        assertEquals(UiText.Res(Res.string.links_visibility_all), LinkVisibility.ALL.label("ФИЗ ПИИКТ 3.2.1"))
    }

    private fun link(url: String, title: String? = null) = subjectLink("link").copy(url = url, title = title)
}
