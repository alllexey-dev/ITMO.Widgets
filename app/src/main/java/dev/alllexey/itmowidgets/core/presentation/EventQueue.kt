package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot effects of a screen (navigation, snackbars, dialogs).
 *
 * Each event is delivered once, to one collector. Events sent while nobody collects, for example between a
 * configuration change and the new view's collector, wait in the buffer and arrive on the next collection.
 */
class EventQueue<E> {

    private val channel = Channel<E>(Channel.BUFFERED)

    val events: Flow<E> = channel.receiveAsFlow()

    suspend fun send(event: E) {
        channel.send(event)
    }
}
