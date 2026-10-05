package dev.alllexey.itmowidgets.core.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.text.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppIconResTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every icon is a row of the icon registry`() {
        val registry = listOf(File(".."), File("."))
            .map { File(it, REGISTRY) }
            .first { it.isFile }
        val ids = registry.readLines().drop(1).filter { it.isNotEmpty() }.map { it.substringBefore('\t') }.toSet()

        AppIcon.entries.forEach { icon -> assertTrue(icon.id, icon.id in ids) }
    }

    @Test
    fun `every icon maps to its ic_ drawable`() {
        AppIcon.entries.forEach { icon ->
            assertEquals(icon.id, "ic_${icon.id}", context.resources.getResourceEntryName(icon.drawableRes()))
        }
    }

    private companion object {
        const val REGISTRY = "docs/design/icons.tsv"
    }
}
