package dev.alllexey.itmowidgets.core.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.alllexey.itmowidgets.core.resources.displayTitle as commonDisplayTitle
import dev.alllexey.itmowidgets.core.resources.host as commonHost
import dev.alllexey.itmowidgets.core.resources.icon as commonIcon
import dev.alllexey.itmowidgets.core.resources.label as commonLabel
import dev.alllexey.itmowidgets.core.resources.linkIcon as commonLinkIcon
import dev.alllexey.itmowidgets.core.resources.title as commonTitle
import dev.alllexey.itmowidgets.core.reviews.tone as commonTone

/**
 * The common link texts and teacher tone of `:shared:core` give what the View helpers of `core/ui` give, until LX-5
 * removes the helpers. The URL corpus repeats the common tables of `SubjectLinkTextsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LinkTextsParityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `category titles and symbols match`() {
        LinkCategory.entries.forEach { category ->
            assertEquals(category.name, category.title().resolve(context), category.commonTitle().resolve(context))
            assertEquals(category.name, category.iconRes(), category.commonIcon().drawableRes())
        }
    }

    @Test
    fun `link symbols match for every category and URL`() {
        LinkCategory.entries.forEach { category ->
            URLS.forEach { url ->
                assertEquals("$category <$url>", linkIconRes(category, url), commonLinkIcon(category, url).drawableRes())
            }
        }
    }

    @Test
    fun `hosts and display titles match`() {
        URLS.forEach { url ->
            listOf(null, "Таблица").forEach { title ->
                val link = link(url, title)
                assertEquals("<$url>", link.host(), link.commonHost())
                assertEquals("<$url> $title", link.displayTitle(), link.commonDisplayTitle())
            }
        }
    }

    @Test
    fun `visibility labels match`() {
        LinkVisibility.entries.forEach { visibility ->
            listOf(null, "ФИЗ ПИИКТ 3.2.1").forEach { audience ->
                assertEquals(
                    "$visibility $audience",
                    visibility.label(audience).resolve(context),
                    visibility.commonLabel(audience).resolve(context),
                )
            }
        }
    }

    @Test
    fun `teacher tone labels and descriptions match`() {
        TeacherLevel.entries.forEach { level ->
            val label = level.commonTone().label.resolve(context)

            assertEquals(level.name, context.getString(level.tone().label), label)
            assertEquals(level.name, level.tone().description(context), level.commonTone().description(label).resolve(context))
        }
    }

    private fun link(url: String, title: String?) = SubjectLink(
        id = "link",
        scope = ResourceScope(subjectId = 1, subjectName = "Физика", periodKey = "2026-1"),
        category = LinkCategory.CHAT,
        url = url,
        title = title,
        visibility = LinkVisibility.PRIVATE,
        flowId = null,
        audienceLabel = null,
        status = SubjectLinkStatus.PRIVATE,
        reviewNote = null,
        score = 0,
        myVote = 0,
        isMine = true,
        reportedByMe = false,
        author = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )

    private companion object {
        val URLS = listOf(
            "https://t.me/+AbCd",
            "https://telegram.me/itmo",
            "https://telegram.dog/itmo",
            "https://www.t.me/itmo",
            "HTTPS://T.ME/Itmo",
            " https://telegram.me/itmo ",
            "https://vk.com/im?sel=c1",
            "https://vk.ru/club1",
            "https://vk.me/join/abc",
            "https://m.vk.com/club1",
            "https://www.vk.com/im?sel=c1",
            "https://VK.Com/club1",
            "\thttps://vk.me/join/abc\n",
            "https://chat.whatsapp.com/x",
            "https://web.telegram.org/a",
            "https://t.me.example.org/x",
            "tg://resolve?domain=itmo",
            "t.me/itmo",
            "https://WWW.Example.ORG/Path",
            "http://user@lms.itmo.ru:8080/course?id=1#top",
            "https://192.168.0.1/x",
            "https://[2001:DB8::1]/x",
            "lms.itmo.ru/course",
            "mailto:dean@itmo.ru",
            "https://",
            "https://t.me/a b",
            "https://t.me/%zz",
            "не ссылка",
            "",
        )
    }
}
