package dev.alllexey.itmowidgets.feature.recordbook.presentation

import android.app.Application
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_reason_sport
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The moved `strings_recordbook.xml` resolves from the module's `Res` with the Russian plural forms (LR-2b). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class RecordbookStringsTest {

    @Test
    fun `sport reason uses the Russian plural forms`() {
        val texts = listOf(1, 2, 5).map { count ->
            runBlocking { getPluralString(Res.plurals.recordbook_reason_sport, count, count) }
        }

        assertEquals(listOf("Спорт: ещё 1 балл", "Спорт: ещё 2 балла", "Спорт: ещё 5 баллов"), texts)
    }
}
