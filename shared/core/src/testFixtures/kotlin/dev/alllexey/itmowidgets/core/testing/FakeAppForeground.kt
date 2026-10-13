package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.platform.AppForeground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** The app's foreground in a test: [comeBack] is one return to the foreground. */
class FakeAppForeground : AppForeground {
    private val returns = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun returns(): Flow<Unit> = returns

    fun comeBack() {
        returns.tryEmit(Unit)
    }
}
