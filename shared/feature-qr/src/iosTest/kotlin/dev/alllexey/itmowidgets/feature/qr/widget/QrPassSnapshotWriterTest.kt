package dev.alllexey.itmowidgets.feature.qr.widget

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.AppGroupSessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class QrPassSnapshotWriterTest {

    private val device = TestDevice()
    private val log = RecordingAppLog()
    private val directory: AppGroupDirectory = device.appGroup(log)
    private val reloads = mutableListOf<String>()
    private val clock = FakeClock(SAVED_AT)
    private val demo = FakeDemoMode(active = true)
    private val remote = FakeRemote()

    @AfterTest
    fun deleteDevice() = device.delete()

    /**
     * The committed fixture is what this writer writes for the demo pass; `QrPassSnapshotTests` (Swift) decodes the
     * same file. A deliberate change to the snapshot deletes the fixture, and this test writes it again (and fails,
     * so the new file is reviewed and committed).
     */
    @Test
    fun theCommittedFixtureIsWhatTheWriterWritesForTheDemoPass() = runTest(UnconfinedTestDispatcher()) {
        val (repository, writer) = graph()
        repository.refreshQrHex(force = true)
        clock.advanceBy(GENERATED_AFTER)

        writer.publish(DemoQr.HEX)

        val written = FileSystem.SYSTEM.read(directory.file(FILE_NAME)) { readUtf8() }
        val fixture = repositoryRoot() / FIXTURE
        if (!FileSystem.SYSTEM.exists(fixture)) {
            FileSystem.SYSTEM.createDirectories(fixture.parent!!)
            FileSystem.SYSTEM.write(fixture) { writeUtf8(pretty(written)) }
            fail("Wrote $FIXTURE; review and commit it")
        }
        val committed = FileSystem.SYSTEM.read(fixture) { readUtf8() }
        assertEquals(Json.parseToJsonElement(committed), Json.parseToJsonElement(written))
    }

    @Test
    fun theDemoPassIsTheSharedGeneratorsCodeWithItsExpiryAndTheWidgetReloads() = runTest(UnconfinedTestDispatcher()) {
        val (repository, writer) = graph()
        repository.refreshQrHex(force = true)
        clock.advanceBy(GENERATED_AFTER)

        writer.publish(DemoQr.HEX)

        val snapshot = assertNotNull(snapshotWriter().read(QrPassSnapshotWriter.FILE))
        assertEquals(SAVED_AT + GENERATED_AFTER, snapshot.generatedAt)
        assertEquals(SAVED_AT + 60.minutes, snapshot.expiresAt)
        assertTrue(snapshot.demo)
        val code = QrCodeGenerator().generate(DemoQr.HEX)
        assertEquals(code.size, snapshot.matrix.size)
        snapshot.matrix.forEachIndexed { y, row ->
            assertEquals(code.size, row.length)
            row.forEachIndexed { x, module -> assertEquals(code.getModule(x, y), module == '1', "module $x,$y") }
        }
        assertEquals(listOf(QrPassSnapshotWriter.QR_WIDGET_KIND), reloads)
    }

    @Test
    fun everyNewPassIsWrittenWhileTheWriterFollowsTheRepository() = runTest(UnconfinedTestDispatcher()) {
        demo.active.value = false
        val (repository, writer) = graph()
        val job = writer.launchIn(backgroundScope)

        remote.hex = PASS_ONE
        repository.refreshQrHex(force = true)
        assertEquals(PASS_ONE.matrix(), snapshotWriter().read(QrPassSnapshotWriter.FILE)?.matrix)

        remote.hex = PASS_TWO
        repository.refreshQrHex(force = true)
        val snapshot = assertNotNull(snapshotWriter().read(QrPassSnapshotWriter.FILE))
        assertEquals(PASS_TWO.matrix(), snapshot.matrix)
        assertFalse(snapshot.demo)
        assertEquals(2, reloads.size)
        job.cancel()
    }

    @Test
    fun signOutLeavesNoSnapshotBehind() = runTest(UnconfinedTestDispatcher()) {
        val (repository, writer) = graph()
        writer.launchIn(backgroundScope)
        repository.refreshQrHex(force = true)
        assertTrue(FileSystem.SYSTEM.exists(directory.file(FILE_NAME)))

        // Sign-out runs every cleaner: the pass cache and the App Group container.
        repository.clearSessionData()
        AppGroupSessionDataCleaner(directory, dispatchers()).clearSessionData()

        assertFalse(FileSystem.SYSTEM.exists(directory.file(FILE_NAME)))
        assertEquals(1, reloads.size)
    }

    @Test
    fun anExpiredPassIsNotWritten() = runTest(UnconfinedTestDispatcher()) {
        val (repository, writer) = graph()
        repository.refreshQrHex(force = true)
        clock.advanceBy(61.minutes)

        writer.publish(DemoQr.HEX)

        assertFalse(FileSystem.SYSTEM.exists(directory.file(FILE_NAME)))
        assertEquals(emptyList(), reloads)
    }

    @Test
    fun aPassTooLongForAVersion1CodeIsLoggedAndNotWritten() = runTest(UnconfinedTestDispatcher()) {
        remote.hex = "A".repeat(TOO_LONG)
        val (repository, writer) = graph()
        repository.refreshQrHex(force = true)

        writer.publish(remote.hex)

        assertNull(snapshotWriter().read(QrPassSnapshotWriter.FILE))
        assertEquals(listOf("WARN:QrPassSnapshot:The pass does not fit a version 1 code"), log.lines)
    }

    private fun TestScope.graph(): Pair<QrCodeRepositoryImpl, QrPassSnapshotWriter> {
        val dispatchers = dispatchers()
        val repository = QrCodeRepositoryImpl(QrCodeLocalDataSourceImpl(device.directories, clock), remote, dispatchers)
        return repository to QrPassSnapshotWriter(repository, snapshotWriter(), demo, clock, log)
    }

    private fun TestScope.dispatchers(): AppDispatchers = UnconfinedTestDispatcher(testScheduler).let {
        AppDispatchers(io = it, default = it, main = it)
    }

    private fun snapshotWriter() = AppGroupSnapshotWriter(directory, reloader = { kind -> reloads += kind })

    private fun String.matrix(): List<String> {
        val code = QrCodeGenerator().generate(this)
        return List(code.size) { y -> CharArray(code.size) { x -> if (code.getModule(x, y)) '1' else '0' }.concatToString() }
    }

    private fun pretty(text: String): String {
        val printer = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        return printer.encodeToString(Json.parseToJsonElement(text)) + "\n"
    }

    private class FakeRemote : QrCodeRemoteDataSource {
        var hex: String = DemoQr.HEX

        override suspend fun getQrHex(): String = hex
    }

    private companion object {
        const val FILE_NAME = "qr-pass-v1.json"
        const val FIXTURE = "iosApp/Tests/UnitTests/Fixtures/qr-pass-v1.json"
        const val PASS_ONE = "0123456789ABCDEF"
        const val PASS_TWO = "FEDCBA9876543210"

        /** One byte more than a version 1, ECC LOW code holds. */
        const val TOO_LONG = 18

        val SAVED_AT = Instant.parse("2026-09-01T08:00:00Z")
        val GENERATED_AFTER = 30.25.seconds
    }
}
