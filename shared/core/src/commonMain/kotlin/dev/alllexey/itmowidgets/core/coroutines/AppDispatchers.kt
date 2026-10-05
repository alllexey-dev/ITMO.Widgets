package dev.alllexey.itmowidgets.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatchers app code switches to. Production binds the `kotlinx.coroutines`
 * defaults once in `di/CoroutinesModule.kt`; tests put every slot on one test
 * dispatcher so the work runs on the test scheduler.
 */
class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
