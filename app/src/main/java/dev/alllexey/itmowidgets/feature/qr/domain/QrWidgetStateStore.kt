package dev.alllexey.itmowidgets.feature.qr.domain

/**
 * Reveal state of a single widget instance.
 *
 * The pass is hidden by default and every instance is tracked separately, so two
 * widgets on the same screen do not reveal each other.
 */
interface QrWidgetStateStore {

    suspend fun getState(appWidgetId: Int): QrWidgetState

    suspend fun setState(appWidgetId: Int, state: QrWidgetState)

    suspend fun clearState(appWidgetId: Int)
}
