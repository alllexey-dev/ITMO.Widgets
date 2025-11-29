package dev.alllexey.itmowidgets.ui.widgets

import android.content.Context
import androidx.core.content.edit
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.data.UserSettingsStorage
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState
import dev.alllexey.itmowidgets.workers.LessonWidgetUpdateWorker
import dev.alllexey.itmowidgets.workers.QrWidgetUpdateWorker

object WidgetUtils {

    fun updateAllWidgets(context: Context) {
        resetQrStates(context)
        QrWidgetUpdateWorker.enqueueImmediateUpdate(context)
        LessonWidgetUpdateWorker.enqueueImmediateUpdate(context)
    }

    fun resetQrStates(context: Context) {
        val appContainer = context.appContainer()
        val prefs = appContainer.storage.prefs
        prefs.all.filter { it -> it.key.startsWith(UserSettingsStorage.QR_WIDGET_STATE_PREFIX) }
            .forEach {
                prefs.edit(commit = true) {
                    putString(it.key, QrWidgetState.SPOILER.name)
                }
            }
    }
}