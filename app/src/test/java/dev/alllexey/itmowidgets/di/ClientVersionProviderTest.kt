package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CO-VER1: the `X-App-Version` of this variant, e.g. `2.3.0-beta.1 (20291); android; github`. */
class ClientVersionProviderTest {

    @Test
    fun `the header names versionName, versionCode, android and the distribution flavor`() {
        val value = NetworkModule.provideClientVersion().headerValue

        assertEquals("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}); android; ${BuildConfig.FLAVOR}", value)
        assertTrue(BuildConfig.FLAVOR, BuildConfig.FLAVOR in setOf("github", "play"))
        assertTrue(value, Regex("""[0-9][0-9A-Za-z.+-]* \([0-9]+\); android; (github|play)""").matches(value))
    }
}
