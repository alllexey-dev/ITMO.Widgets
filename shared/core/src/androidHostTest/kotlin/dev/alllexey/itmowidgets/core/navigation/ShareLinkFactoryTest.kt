package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareLinkFactoryTest {
    private val factories = listOf("https://widgets.alllexey.dev", "https://dev.widgets.alllexey.dev").map(::ShareLinkFactory)

    @Test fun `links of both builds parse back to what they share`() {
        factories.forEach { links ->
            assertEquals(AppLink.Profile(123456), AppLinks.parse(links.profile(123456)))
            assertEquals(AppLink.SportLesson(987654321), AppLinks.parse(links.sportLesson(987654321)))
            assertEquals(AppLink.PredictedSportLesson(987654321), AppLinks.parse(links.predictedSportLesson(987654321)))
        }
    }

    @Test fun `the prod build shares prod links`() {
        val links = factories.first()
        assertEquals("https://widgets.alllexey.dev/u/123456", links.profile(123456))
        assertEquals("https://widgets.alllexey.dev/sport/42", links.sportLesson(42))
        assertEquals("https://widgets.alllexey.dev/sport/p/42", links.predictedSportLesson(42))
    }

    @Test fun `non-positive identifiers are rejected`() {
        val links = factories.first()
        listOf(0, -1).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { links.profile(id) }
            assertThrows(IllegalArgumentException::class.java) { links.sportLesson(id.toLong()) }
            assertThrows(IllegalArgumentException::class.java) { links.predictedSportLesson(id.toLong()) }
        }
    }
}
