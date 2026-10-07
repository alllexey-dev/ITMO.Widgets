package dev.alllexey.itmowidgets.core.work

import android.app.Application
import androidx.work.ListenableWorker.Result
import androidx.work.workDataOf
import dev.alllexey.itmowidgets.core.notification.FcmMessageWorker
import dev.alllexey.itmowidgets.core.notification.FcmPayloadDispatcher
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.notification.FcmTokenWorker
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.IdentitySyncWork
import dev.alllexey.itmowidgets.core.session.IdentitySyncWorker
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The FCM and identity workers read their dependencies from Koin (KM-12a), built as WorkManager builds them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CoreWorkersKoinTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a message for the signed-in user reaches the handler of its type`() = runTest {
        val handler = RecordingHandler("known")
        val context = startWorkerGraph(messageGraph(handler, currentIsu = 100001))
        val worker = buildWorker<FcmMessageWorker>(context, message(recipientIsu = 100001))

        assertEquals(Result.success(), worker.doWork())
        assertEquals(1, handler.received.size)
    }

    @Test
    fun `a message for another user is dropped before the dispatcher`() = runTest {
        val handler = RecordingHandler("known")
        val context = startWorkerGraph(messageGraph(handler, currentIsu = 100001))
        val worker = buildWorker<FcmMessageWorker>(context, message(recipientIsu = 100002))

        assertEquals(Result.success(), worker.doWork())
        assertEquals(0, handler.received.size)
    }

    @Test
    fun `the token worker syncs through Koin's token sync and retries a failure`() = runTest {
        var syncs = 0
        var failure: Exception? = null
        val context = startWorkerGraph(
            module {
                single<FcmTokenSync> {
                    FcmTokenSync {
                        syncs++
                        failure?.let { throw it }
                    }
                }
            }
        )

        assertEquals(Result.success(), buildWorker<FcmTokenWorker>(context).doWork())
        failure = IOException("offline")
        assertEquals(Result.retry(), buildWorker<FcmTokenWorker>(context).doWork())
        assertEquals(2, syncs)
    }

    @Test
    fun `the identity worker reads Koin's sync and gives up after the last attempt`() = runTest {
        val sync = SwitchableIdentitySync()
        val context = startWorkerGraph(module { single<BackendIdentitySync> { sync } })

        assertEquals(Result.retry(), buildWorker<IdentitySyncWorker>(context).doWork())
        val lastAttempt = IdentitySyncWork.MAX_ATTEMPTS - 1
        assertEquals(Result.failure(), buildWorker<IdentitySyncWorker>(context, runAttemptCount = lastAttempt).doWork())
        sync.uploaded = true
        assertEquals(Result.success(), buildWorker<IdentitySyncWorker>(context).doWork())
    }

    private fun messageGraph(handler: FcmPayloadHandler, currentIsu: Int) = module {
        single<CurrentUserProvider> { FixedUser(currentIsu) }
        single<SessionTokenStore> { FakeSessionTokenStore(signedIn = true) }
        single<BackendGate> { FakeBackendGate(optedIn = true) }
        single<FcmPayloadDispatcher> { FcmPayloadDispatcher(listOf(handler), RecordingDiagnostics()) }
    }

    private fun message(recipientIsu: Int) = workDataOf(
        FcmWork.PAYLOAD to """{"type":"known","payload":{}}""",
        FcmWork.RECIPIENT to recipientIsu,
    )

    private class FixedUser(private val isu: Int) : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(isu = isu, name = null, pictureUrl = null)
    }

    private class RecordingHandler(override val type: String) : FcmPayloadHandler {
        val received = mutableListOf<JsonElement>()

        override suspend fun handle(payload: JsonElement) {
            received += payload
        }
    }

    private class SwitchableIdentitySync : BackendIdentitySync {
        var uploaded = false

        override suspend fun sync(scheduleRetry: Boolean): Boolean = uploaded
    }
}
