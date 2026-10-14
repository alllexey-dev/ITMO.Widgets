package dev.alllexey.itmowidgets.core.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/** [AppForeground] from UIKit: a return is the app becoming active, as SwiftUI's `scenePhase` turning `.active`. */
object IosAppForeground : AppForeground {
    override fun returns(): Flow<Unit> = callbackFlow {
        val center = NSNotificationCenter.defaultCenter
        // No queue: delivered on the posting thread, UIKit's main one.
        val observer = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) {
            trySend(Unit)
        }
        awaitClose { center.removeObserver(observer) }
    }
}
