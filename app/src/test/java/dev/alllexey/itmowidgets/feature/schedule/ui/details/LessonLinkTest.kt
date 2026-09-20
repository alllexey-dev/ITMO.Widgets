package dev.alllexey.itmowidgets.feature.schedule.ui.details

import org.junit.Assert.assertEquals
import org.junit.Test

class LessonLinkTest {
    @Test
    fun `the host names the platform whatever the field is called`() {
        assertEquals("bbb.itmo.ru", lessonLinkHost("https://bbb.itmo.ru/b/abc-def"))
        assertEquals("zoom.us", lessonLinkHost(" https://www.zoom.us/j/123?pwd=x "))
        assertEquals("meet.google.com", lessonLinkHost("https://meet.google.com/abc-defg-hij"))
    }

    @Test
    fun `text that is not a URL is shown as it is`() {
        assertEquals("см. в чате курса", lessonLinkHost("см. в чате курса"))
        assertEquals("ссылка придёт позже", lessonLinkHost(" ссылка придёт позже "))
    }
}
