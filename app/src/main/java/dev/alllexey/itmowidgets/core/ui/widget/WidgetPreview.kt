package dev.alllexey.itmowidgets.core.ui.widget

import android.content.Context
import android.os.Bundle
import android.view.View
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import kotlinx.coroutines.CoroutineScope

/** Preview-only views: no AppWidgetManager updates, PendingIntents, or network requests. */
interface WidgetPreview {
    val view: View
    fun bind(settings: WidgetPreviewSettings)
    fun saveState(): Bundle? = null
    fun restoreState(state: Bundle) = Unit
    suspend fun awaitReady() = Unit
    fun refresh() = Unit
    fun stop() = Unit
    fun close() = Unit
}

interface WidgetPreviewFactory {
    fun preload(context: Context, scope: CoroutineScope) = Unit
    fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview
}
