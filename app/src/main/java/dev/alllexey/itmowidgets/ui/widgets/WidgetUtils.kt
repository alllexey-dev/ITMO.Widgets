package dev.alllexey.itmowidgets.ui.widgets

import android.content.Context
import dev.alllexey.itmowidgets.workers.LessonWidgetUpdateWorker
import dev.alllexey.itmowidgets.workers.QrWidgetUpdateWorker

object WidgetUtils {

    fun updateAllWidgets(context: Context) {
        QrWidgetUpdateWorker.enqueueImmediateUpdate(context)
        LessonWidgetUpdateWorker.enqueueImmediateUpdate(context)
    }
}