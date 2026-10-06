package dev.alllexey.itmowidgets.feature.schedule.presentation

import android.app.Application
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_break_range
import dev.alllexey.itmowidgets.shared.feature.schedule.user_schedule_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The moved `strings_schedule.xml` resolves from the module's `Res` with its positional arguments (LS-2b). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class ScheduleStringsTest {

    @Test
    fun `break range fills both times in order`() {
        val text = runBlocking { getString(Res.string.schedule_break_range, "11:40", "11:50") }

        assertEquals("Перерыв с 11:40 по 11:50", text)
    }

    @Test
    fun `user schedule title names the user`() {
        val text = runBlocking { getString(Res.string.user_schedule_title, "Иван Иванов") }

        assertEquals("Расписание: Иван Иванов", text)
    }
}
