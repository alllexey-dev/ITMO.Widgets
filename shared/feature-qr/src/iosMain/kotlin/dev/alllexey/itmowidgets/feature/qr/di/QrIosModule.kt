package dev.alllexey.itmowidgets.feature.qr.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.feature.qr.widget.QrPassSnapshotWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * The iOS side of the QR pass, beside [qrModule]: the App Group snapshot the QR widget reads. The writer starts with
 * the graph and follows the pass and the QR widget options for the life of the app process, on the main queue
 * (WidgetKit reloads are asked there); the repository does its own file work on the IO dispatcher.
 */
val qrIosModule = module {
    single(createdAtStart = true) {
        QrPassSnapshotWriter(get(), get(), get(), get(), get(), get(), get()).also { writer ->
            writer.launchIn(CoroutineScope(SupervisorJob() + get<AppDispatchers>().main))
        }
    }
}
