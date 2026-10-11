package dev.alllexey.itmowidgets.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** On Android the widgets take the wallpaper's colours where there are some, as the app's screens do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetPalettesAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theWallpaperChoiceTakesTheWallpaperSchemeOnApi31AndLater() {
        val wallpaper = checkNotNull(wallpaperPalette(context))

        assertEquals(widgetPaletteOf(wallpaper.light, wallpaper.dark), ThemeSpec().widgetPalette(context))
    }

    /** Only the SDK 35 runtime is available offline, so the check sees API 30 through `SDK_INT`. */
    @Test
    fun belowApi31TheWallpaperChoiceIsTheBrandPalette() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)
        try {
            assertEquals(ThemeSpec(accent = AccentColor.BRAND).widgetPalette(), ThemeSpec().widgetPalette(context))
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.VANILLA_ICE_CREAM)
        }
    }

    @Test
    fun aPresetIgnoresTheWallpaper() {
        val purple = ThemeSpec(accent = AccentColor.PURPLE)
        assertEquals(purple.widgetPalette(), purple.widgetPalette(context))
    }
}
