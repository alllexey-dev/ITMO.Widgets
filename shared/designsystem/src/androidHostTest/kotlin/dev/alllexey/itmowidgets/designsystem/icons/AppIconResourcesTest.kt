package dev.alllexey.itmowidgets.designsystem.icons

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.allDrawableResources
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconResourcesTest {

    @Test
    fun `every AppIcon resolves to the drawable named after its registry id`() {
        val expected = AppIcon.entries.associateWith { Res.allDrawableResources["ic_${it.id}"] }

        assertEquals(expected, AppIcon.entries.associateWith { it.drawable })
    }
}
