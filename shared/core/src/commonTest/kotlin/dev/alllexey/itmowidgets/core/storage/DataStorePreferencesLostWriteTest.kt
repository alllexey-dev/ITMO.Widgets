package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.intPreferencesKey
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path

/**
 * androidx.datastore 1.2.1 tags a read made while a write holds the lock with the write's new version,
 * so a `data` collector whose first read lands inside a write emits the old value and then drops the write.
 * These tests run over a real file because the in-memory fake cannot race.
 */
class DataStorePreferencesLostWriteTest {
    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-core-lost-write-${Random.nextLong().toULong()}"
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val serializer = GatedSerializer()
    private val store = CounterPreferences(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(SystemFileSystem, serializer) { root / "counter.preferences_pb" },
            scope = scope
        )
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        SystemFileSystem.deleteRecursively(root)
    }

    @Test
    fun aCollectorWhoseFirstReadLandsInsideAWriteSeesTheWrite() = runTest(timeout = 30.seconds) {
        withContext(Dispatchers.Default) {
            store.setCount(0)
            store.observeCount().first()
            val release = serializer.holdNextWrite()

            scope.launch { store.setCount(1) }
            serializer.writeHeld.await()
            val seen = MutableStateFlow(-1)
            val collector = scope.launch { store.observeCount().collect { seen.value = it } }
            serializer.readDuringHeldWrite.await()
            release.complete(Unit)

            val last = withTimeoutOrNull(5.seconds) { seen.first { it == 1 } } ?: seen.value
            collector.cancelAndJoin()
            assertEquals(1, last)
        }
    }

    @Test
    fun collectorsStartedRightBeforeAWriteSeeEveryWrite() = runTest(timeout = 300.seconds) {
        withContext(Dispatchers.Default) {
            store.setCount(0)
            var lost = 0
            repeat(STRESS_ROUNDS) { round ->
                val value = round + 1
                val seen = MutableStateFlow(-1)
                val collector = scope.launch { store.observeCount().collect { seen.value = it } }
                store.setCount(value)
                if (withTimeoutOrNull(500.milliseconds) { seen.first { it == value } } == null) lost++
                collector.cancelAndJoin()
            }
            assertEquals(0, lost, "of $STRESS_ROUNDS writes, these never reached a collector started right before them")
        }
    }

    private class CounterPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {
        fun observeCount() = observe { it[COUNT] ?: 0 }

        suspend fun setCount(value: Int) = write(COUNT, value)
    }

    /** [PreferencesSerializer] that can hold one write after DataStore has bumped the version for it. */
    private class GatedSerializer : OkioSerializer<Preferences> by PreferencesSerializer {
        private var gate: CompletableDeferred<Unit>? = null
        val writeHeld = CompletableDeferred<Unit>()
        val readDuringHeldWrite = CompletableDeferred<Unit>()

        fun holdNextWrite(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gate = it }

        override suspend fun readFrom(source: BufferedSource): Preferences {
            if (writeHeld.isCompleted && gate?.isCompleted == false) readDuringHeldWrite.complete(Unit)
            return PreferencesSerializer.readFrom(source)
        }

        override suspend fun writeTo(t: Preferences, sink: BufferedSink) {
            gate?.let { held ->
                writeHeld.complete(Unit)
                held.await()
                gate = null
            }
            PreferencesSerializer.writeTo(t, sink)
        }
    }

    private companion object {
        val COUNT = intPreferencesKey("count")
        const val STRESS_ROUNDS = 2000
    }
}
