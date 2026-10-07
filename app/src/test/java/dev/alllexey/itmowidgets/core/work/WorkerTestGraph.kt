package dev.alllexey.itmowidgets.core.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import java.lang.reflect.Proxy
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * The Koin graph a worker test runs on: Robolectric's plain `Application` (no Hilt, no `onCreate()` start) with only
 * the [definitions] the worker reads, started before the worker is built, so `KoinStarter` finds it and keeps it.
 * Pair with `StopKoinRule` and `@Config(application = Application::class)`.
 */
internal fun startWorkerGraph(definitions: Module): Context {
    val application = ApplicationProvider.getApplicationContext<Application>()
    startKoin {
        androidContext(application)
        modules(definitions)
    }
    return application
}

/** A worker as WorkManager's default factory builds it: by its `(Context, WorkerParameters)` constructor. */
internal inline fun <reified W : ListenableWorker> buildWorker(
    context: Context,
    input: Data = Data.EMPTY,
    runAttemptCount: Int = 0,
): W = TestListenableWorkerBuilder.from(context, W::class.java)
    .setInputData(input)
    .setRunAttemptCount(runAttemptCount)
    .build()

/** A port the code under test must not call on this path: any call fails the test with the method's name. */
internal inline fun <reified T : Any> unusedPort(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { _, method, _ ->
    if (method.name == "toString") "unused ${T::class.java.simpleName}" else error("unexpected ${method.name}")
} as T
