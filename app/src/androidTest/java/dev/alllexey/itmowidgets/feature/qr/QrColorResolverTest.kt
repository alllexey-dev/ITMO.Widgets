package dev.alllexey.itmowidgets.feature.qr

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget renders from the application context, whose theme carries no Material
 * attributes. An unresolved attribute used to yield colour `0` — fully transparent —
 * and the widget came out invisible.
 */
@RunWith(AndroidJUnit4::class)
class QrColorResolverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun dynamicColorsStayOpaqueOutsideAnActivity() {
        val resolver = QrColorResolver(context, StubPreferences)

        val (background, foreground) = resolver.getQrColors(dynamic = true)

        assertEquals(255, Color.alpha(background))
        assertEquals(255, Color.alpha(foreground))
    }

    @Test
    fun staticColorsAreBlackOnWhite() {
        val resolver = QrColorResolver(context, StubPreferences)

        assertEquals(Color.WHITE to Color.BLACK, resolver.getQrColors(dynamic = false))
    }

    private object StubPreferences : QrAppearancePreferences {
        override suspend fun useDynamicColors(): Boolean = true

        override suspend fun isSpoilerEnabled(): Boolean = true

        override suspend fun spoilerAnimationType(): QrAnimationType = QrAnimationType.CIRCLE
    }
}
