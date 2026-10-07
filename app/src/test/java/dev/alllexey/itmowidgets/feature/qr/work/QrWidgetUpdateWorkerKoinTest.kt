package dev.alllexey.itmowidgets.feature.qr.work

import android.app.Application
import androidx.work.ListenableWorker.Result
import dev.alllexey.itmowidgets.core.work.buildWorker
import dev.alllexey.itmowidgets.core.work.startWorkerGraph
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The QR widget worker reads the pass from Koin (KM-12a), built as WorkManager builds it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QrWidgetUpdateWorkerKoinTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `without a widget the worker succeeds before it reads the graph`() = runTest {
        // An empty graph: the repository, the state store and the bitmaps are resolved only when a widget exists.
        val context = startWorkerGraph(module { })

        assertEquals(Result.success(), buildWorker<QrWidgetUpdateWorker>(context).doWork())
    }
}
