package dev.alllexey.itmowidgets.feature.home.domain

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import kotlinx.coroutines.flow.Flow

/** What the device says right now; read again on every return to the screen. */
interface HomeHintStatus {
    /** True as well when the launcher cannot pin widgets: nothing to nudge toward. */
    suspend fun anyWidgetPlaced(): Boolean

    suspend fun notificationsEnabled(): Boolean
}

/** Hints the user closed; kept per installation, so signing out does not bring them back. */
interface HomeHintStore {
    fun observeDismissed(): Flow<Set<HomeHint>>

    suspend fun dismiss(hint: HomeHint)
}

/** Card kinds hidden in settings. */
interface HomeCardPreferences {
    fun observeHidden(): Flow<Set<HomeCardKind>>
}
