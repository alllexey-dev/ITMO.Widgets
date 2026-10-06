package dev.alllexey.itmowidgets.feature.resources.domain

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCategoryGuessTest {
    @Test fun `known sites give their category`() {
        val expected = mapOf(
            "https://docs.google.com/spreadsheets/d/abc/edit#gid=0" to LinkCategory.SCORES,
            "https://docs.google.com/forms/d/e/abc/viewform" to LinkCategory.QUEUE,
            "https://github.com/itmo/labs" to LinkCategory.TASKS,
            "https://www.youtube.com/watch?v=abc" to LinkCategory.RECORDINGS,
            "https://m.youtube.com/playlist?list=abc" to LinkCategory.RECORDINGS,
            "https://youtu.be/abc" to LinkCategory.RECORDINGS,
            "https://vk.com/video-123_456" to LinkCategory.RECORDINGS,
            "https://vkvideo.ru/video-123_456" to LinkCategory.RECORDINGS,
            "https://www.notion.so/page-abc" to LinkCategory.NOTES,
            "https://team.notion.site/page-abc" to LinkCategory.NOTES,
            "https://lms.itmo.ru/course/view.php?id=1" to LinkCategory.MATERIALS,
            "https://t.me/+invite" to LinkCategory.CHAT,
            "https://vk.me/join/abc" to LinkCategory.CHAT,
            "https://chat.whatsapp.com/abc" to LinkCategory.CHAT,
        )

        expected.forEach { (url, category) -> assertEquals(url, category, guessCategory(url)) }
    }

    @Test fun `a link typed without a scheme is still recognised`() {
        assertEquals(LinkCategory.TASKS, guessCategory("github.com/itmo/labs"))
    }

    @Test fun `other sites and other pages of known hosts give nothing`() {
        listOf(
            "https://docs.google.com/document/d/abc",
            "https://vk.com/club1",
            "https://example.org/github.com",
            "https://notgithub.com/repo",
            "not a link",
            "",
        ).forEach { assertNull(it, guessCategory(it)) }
    }
}
