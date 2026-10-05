package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {
    private val hosts = listOf("https://widgets.alllexey.dev", "https://dev.widgets.alllexey.dev")

    @Test fun `profile links on both hosts carry the ISU`() {
        hosts.forEach { host ->
            listOf("/u/123456", "/u/123456/", "/u/123456?utm=tg", "/u/123456#top", "/u/123456/?a=1#b").forEach { path ->
                assertEquals(host + path, AppLink.Profile(123456), AppLinks.parse(host + path))
            }
        }
    }

    @Test fun `sport links on both hosts carry the lesson id and predicted links the prototype id`() {
        hosts.forEach { host ->
            assertEquals(AppLink.SportLesson(987654321), AppLinks.parse("$host/sport/987654321"))
            assertEquals(AppLink.SportLesson(987654321), AppLinks.parse("$host/sport/987654321/"))
            assertEquals(AppLink.PredictedSportLesson(987654321), AppLinks.parse("$host/sport/p/987654321"))
            assertEquals(AppLink.PredictedSportLesson(987654321), AppLinks.parse("$host/sport/p/987654321/?x=1"))
        }
        assertEquals(AppLink.SportLesson(Long.MAX_VALUE), AppLinks.parse("https://widgets.alllexey.dev/sport/${Long.MAX_VALUE}"))
        assertEquals(AppLink.Profile(Int.MAX_VALUE), AppLinks.parse("https://widgets.alllexey.dev/u/${Int.MAX_VALUE}"))
    }

    @Test fun `unreadable identifiers under the app prefixes are malformed`() {
        listOf(
            "/u/", "/u/0", "/u/012", "/u/-1", "/u/abc", "/u/2147483648", "/u/1/2", "/u/1//",
            "/sport/", "/sport/x", "/sport/0", "/sport/9223372036854775808", "/sport/1/2",
            "/sport/p", "/sport/p/", "/sport/p/0", "/sport/p/x", "/sport/p/1/2"
        ).forEach { path ->
            assertEquals(path, AppLink.Malformed, AppLinks.parse("https://dev.widgets.alllexey.dev$path"))
        }
    }

    @Test fun `links that are not the app's are ignored`() {
        listOf(
            "http://widgets.alllexey.dev/u/1",
            "https://example.com/u/1",
            "https://widgets.alllexey.dev.example.com/u/1",
            "https://widgets.alllexey.dev/app/x",
            "https://widgets.alllexey.dev/",
            "https://widgets.alllexey.dev",
            "https://widgets.alllexey.dev/user/1",
            "not a url",
            "",
            null
        ).forEach { url -> assertNull(url, AppLinks.parse(url)) }
    }
}
