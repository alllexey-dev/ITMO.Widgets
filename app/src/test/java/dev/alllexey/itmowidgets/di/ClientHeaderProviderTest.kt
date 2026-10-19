package dev.alllexey.itmowidgets.di

import android.app.Application
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.client.BuildIdentity
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
    fun `the header names android, the build, its identity, the API level and the install`() = runTest {
        val installIds = InstallIdPreferences(InMemoryPreferencesDataStore())

        val value = clientHeader(installIds).value()

        // A test build is a debug build: no commit; the channel and source are this machine's build identity.
        val channel = if (BuildConfig.BUILD_CHANNEL == "official") BuildConfig.FLAVOR else "custom"
        assertEquals(
            "kind=android; version=${BuildConfig.VERSION_NAME}; build=${BuildConfig.VERSION_CODE}; " +
                "channel=$channel; source=${BuildConfig.BUILD_SOURCE}; os=35; install=${installIds.installId()}",
            value
        )
    }

    @Test
    fun `an official build sends its flavor and a custom one says custom, with the source and commit as built`() {
        assertEquals(
            BuildIdentity(channel = "play", source = "alllexey-dev/ITMO.Widgets", commit = "0123456789ab"),
            buildIdentity("play", "official", "alllexey-dev/ITMO.Widgets", "0123456789ab")
        )
        assertEquals(
            BuildIdentity(channel = "custom", source = "AlexPer777/ITMO.Widgets", commit = "ba9876543210"),
            buildIdentity("github", "custom", "AlexPer777/ITMO.Widgets", "ba9876543210")
        )
        assertEquals(
            BuildIdentity(channel = "custom", source = "local", commit = null),
            buildIdentity("github", "custom", "local", "")
        )
    }
}
