package dev.alllexey.itmowidgets.di

import android.app.Application
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.storage.InstallIdPreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR 0034: the `X-Client` of this variant, e.g. `kind=android; version=2.3.0; build=20300; channel=play; ...`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ClientHeaderProviderTest {

    @Test
    fun `the header names android, the build, the flavor, the API level and the install`() = runTest {
        val installIds = InstallIdPreferences(InMemoryPreferencesDataStore())

        val value = clientHeader(installIds).value()

        assertEquals(
            "kind=android; version=${BuildConfig.VERSION_NAME}; build=${BuildConfig.VERSION_CODE}; " +
                "channel=${BuildConfig.FLAVOR}; os=35; install=${installIds.installId()}",
            value
        )
    }
}
