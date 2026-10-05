package dev.alllexey.itmowidgets.feature.qr.data.local

import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** `cache/qr_hex` on okio's fake file system: the 2.2 and v2.0 files, the 60-minute expiry and clear. */
class QrCodeLocalDataSourceImplTest {

    private val clock = FakeClock(SAVED_AT)
    private var fileSystem: FakeFileSystem = fakeFileSystemOf()

    @AfterTest
    fun tearDown() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun readsThe22FileWithItsSaveTime() {
        fileSystem = fakeFileSystemOf(QR_HEX to "${SAVED_AT.toEpochMilliseconds()}|$PASS_HEX", clock = clock)
        clock.advanceBy(10.minutes)

        val source = source()

        assertEquals(PASS_HEX, source.get())
        assertEquals(QrCodeSnapshot(PASS_HEX, (SAVED_AT + 60.minutes).toEpochMilliseconds()), source.snapshot())
    }

    @Test
    fun readsTheV20FileWithTheHexAloneAsFreshlySaved() {
        fileSystem = fakeFileSystemOf(QR_HEX to "$PASS_HEX\n", clock = clock)

        val source = source()

        assertEquals(PASS_HEX, source.get())
        assertEquals((SAVED_AT + 60.minutes).toEpochMilliseconds(), source.snapshot()?.expiresAtMillis)
    }

    @Test
    fun ignoresAnUnreadableFile() {
        fileSystem = fakeFileSystemOf(QR_HEX to "not-a-time|$PASS_HEX", clock = clock)

        assertNull(source().get(allowExpired = true))
    }

    @Test
    fun writesThe22Format() {
        val source = source()

        source.save(PASS_HEX)

        assertEquals("${SAVED_AT.toEpochMilliseconds()}|$PASS_HEX", fileSystem.read(QR_HEX.toPath()) { readUtf8() })
        assertEquals(PASS_HEX, source().get())
    }

    @Test
    fun aPassExpiresExactlySixtyMinutesAfterItWasSaved() = runTest {
        val source = source()
        source.save(PASS_HEX)

        clock.advanceBy(60.minutes - 1.milliseconds)
        assertEquals(PASS_HEX, source.get())
        assertEquals(PASS_HEX, source.observe().first())

        clock.advanceBy(1.milliseconds)
        assertNull(source.get())
        assertNull(source.snapshot())
        assertEquals(PASS_HEX, source.get(allowExpired = true))
    }

    @Test
    fun clearDeletesTheFileAndForgetsThePass() {
        fileSystem = fakeFileSystemOf(QR_HEX to "${SAVED_AT.toEpochMilliseconds()}|$PASS_HEX", clock = clock)
        val source = source()

        source.clear()

        assertFalse(fileSystem.exists(QR_HEX.toPath()))
        assertNull(source.get(allowExpired = true))
        assertNull(source().get(allowExpired = true))
    }

    private fun source() = QrCodeLocalDataSourceImpl(AtomicTextFile(QR_HEX.toPath(), fileSystem), clock)

    private companion object {
        const val QR_HEX = "/cache/qr_hex"

        /** Synthetic, not a real pass. */
        const val PASS_HEX = "001122aabbccddeeff"
        val SAVED_AT = Instant.parse("2026-09-01T08:00:00Z")
    }
}
