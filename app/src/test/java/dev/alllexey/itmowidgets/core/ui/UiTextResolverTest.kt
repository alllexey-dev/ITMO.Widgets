package dev.alllexey.itmowidgets.core.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.allPluralStringResources
import dev.alllexey.itmowidgets.shared.core.allStringResources
import dev.alllexey.itmowidgets.shared.core.schedule_lesson_count
import dev.alllexey.itmowidgets.shared.core.teacher_level_description
import dev.alllexey.itmowidgets.shared.core.teacher_level_mixed
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The Views resolver (generated key -> `R` table) and the CMP resolver give the same text for every string
 * `:shared:core` exports. Plural rules follow the locale on both sides, so the test runs in Russian, as the app does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class UiTextResolverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultLocale = Locale.getDefault()

    @Before
    fun russianDefaultLocale() = Locale.setDefault(Locale.forLanguageTag("ru"))

    @After
    fun restoreDefaultLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun `every exported string resolves the same in Views and CMP`() {
        val formats = catalogEntries("string")
        assertTrue("Only ${formats.size} exported strings", formats.size >= MIN_EXPORTED_STRINGS)
        assertEquals(formats.keys, Res.allStringResources.keys)

        Res.allStringResources.forEach { (key, resource) ->
            val text = UiText.Res(resource, argumentsFor(formats.getValue(key)))

            assertEquals(key, runBlocking { text.resolve() }, text.resolve(context))
        }
    }

    @Test
    fun `every exported plural resolves the same Russian forms in Views and CMP`() {
        val formats = catalogEntries("plurals")
        assertEquals(formats.keys, Res.allPluralStringResources.keys)

        Res.allPluralStringResources.forEach { (key, resource) ->
            PLURAL_COUNTS.forEach { count ->
                val text = UiText.Plural(resource, count, listOf(count))

                assertEquals("$key $count", runBlocking { text.resolve() }, text.resolve(context))
            }
        }
    }

    @Test
    fun `a Russian plural takes the one, few and many forms`() {
        val texts = PLURAL_COUNTS.map { count ->
            UiText.Plural(Res.plurals.schedule_lesson_count, count, listOf(count)).resolve(context)
        }

        assertEquals(listOf("1 пара", "2 пары", "5 пар", "11 пар", "21 пара"), texts)
    }

    @Test
    fun `a nested UiText argument resolves first on both sides`() {
        val text = UiText.Res(Res.string.teacher_level_description, listOf(UiText.Res(Res.string.teacher_level_mixed)))

        assertEquals("Тон отзывов: Смешанные", text.resolve(context))
        assertEquals("Тон отзывов: Смешанные", runBlocking { text.resolve() })
    }

    /** Key -> text of each `<string>` or `<plurals>` (first item) of the core catalog files. */
    private fun catalogEntries(tag: String): Map<String, String> {
        val dir = listOf(File(".."), File("."))
            .map { File(it, CORE_CATALOG) }
            .first { it.isDirectory }
        return CORE_FILES.flatMap { name ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, name))
                .documentElement.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }.associate { it.getAttribute("name") to it.textContent }
    }

    /** An Int for each `%n$d`, a String for each `%n$s`, in position order. */
    private fun argumentsFor(format: String): List<Any> = PLACEHOLDER.findAll(format)
        .map { it.groupValues[1].toInt() to it.groupValues[2] }
        .distinct()
        .sortedBy { it.first }
        .map { (position, type) -> if (type == "d") position * 7 else "аргумент $position" }
        .toList()

    private companion object {
        const val CORE_CATALOG = "shared/core/src/commonMain/composeResources/values"
        val CORE_FILES = listOf("strings_common.xml", "strings_platform.xml")
        const val MIN_EXPORTED_STRINGS = 60
        val PLURAL_COUNTS = listOf(1, 2, 5, 11, 21)
        val PLACEHOLDER = Regex("""%(\d+)\$([ds])""")
    }
}
