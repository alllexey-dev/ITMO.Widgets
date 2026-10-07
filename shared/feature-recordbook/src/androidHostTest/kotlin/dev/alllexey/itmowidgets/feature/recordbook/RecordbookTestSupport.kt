package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.rules.TestWatcher
import org.junit.runner.Description

private val MOSCOW: TimeZone = TimeZone.of("Europe/Moscow")

/** `Dispatchers.Main` on [dispatcher] for one test; `runTest` shares its scheduler through `Dispatchers.Main`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {

    /** Injected into the class under test: every slot on [dispatcher]. */
    val appDispatchers: AppDispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Every slot on [dispatcher] but `io`, which runs on real threads: for race tests whose fake network blocks the calling
 * thread until the test releases it, which on the single test thread would never happen.
 */
fun blockingIoAppDispatchers(dispatcher: TestDispatcher): AppDispatchers =
    AppDispatchers(io = Dispatchers.IO, default = dispatcher, main = dispatcher)

/** A wall clock a test moves by hand. */
class MutableClock(var now: Instant) : Clock {
    fun advance(duration: Duration) {
        now += duration
    }

    override fun now(): Instant = now
}

/** The academic time of [clock] in Moscow, for tests that also hand the same clock to the code under test. */
class ClockAcademicTime(private val clock: Clock) : AcademicTimeProvider {
    override val timeZone: TimeZone = MOSCOW

    override fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    override fun now(): Instant = clock.now()
}

/** An academic clock in Moscow that a test moves by setting [current]. */
class MutableAcademicTime(var current: LocalDateTime) : AcademicTimeProvider {
    override val timeZone: TimeZone = MOSCOW

    override fun today(): LocalDate = current.date

    override fun now(): Instant = current.toInstant(timeZone)
}

/** The app's private directories under [root], as Android lays them out (`files`, `cache`, `no_backup`). */
fun directoriesAt(root: File): AppDirectories = object : AppDirectories {
    override val files: Path = File(root, "files").toOkioPath()
    override val cache: Path = File(root, "cache").toOkioPath()
    override val noBackup: Path = File(root, "no_backup").toOkioPath()
}
