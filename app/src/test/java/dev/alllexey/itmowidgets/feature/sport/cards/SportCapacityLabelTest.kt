package dev.alllexey.itmowidgets.feature.sport.cards

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The details sheet's free-place label takes the Russian plural form of the presenter's free count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class SportCapacityLabelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `free place label follows Russian plural forms`() {
        listOf(
            1 to "Свободное место", 2 to "Свободных места", 4 to "Свободных места",
            5 to "Свободных мест", 11 to "Свободных мест", 21 to "Свободное место", 0 to "Свободных мест"
        ).forEach { (available, expected) ->
            assertEquals("$available", expected,
                context.resources.getQuantityString(R.plurals.sport_capacity_free_label, available))
        }
        assertEquals("нет", context.getString(R.string.sport_capacity_none))
    }
}
