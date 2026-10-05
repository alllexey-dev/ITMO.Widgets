package dev.alllexey.itmowidgets.core.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.text.toUiText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class AppErrorTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val errors = listOf(
        AppError.Network,
        AppError.Unauthorized,
        AppError.Forbidden,
        AppError.Restricted,
        AppError.NotFound,
        AppError.CustomServicesDisabled,
        AppError.DemoUnavailable,
        AppError.Unknown()
    )

    @Test
    fun `every error has its own message`() {
        assertEquals(errors.size, errors.map { it.textResource().key }.distinct().size)
    }

    @Test
    fun `the Views id and the common text of an error name the same key`() {
        errors.forEach { error ->
            assertEquals(error.textResource().key, context.resources.getResourceEntryName(error.messageRes()))
        }
    }

    @Test
    fun `an error resolves to its released text`() {
        assertEquals("Нет связи. Проверьте интернет.", AppError.Network.toUiText().resolve(context))
        assertEquals(UiText.Res(AppError.Restricted.textResource()), AppError.Restricted.toUiText())
    }
}
