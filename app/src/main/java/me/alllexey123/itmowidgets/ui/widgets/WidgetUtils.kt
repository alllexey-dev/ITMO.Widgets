package me.alllexey123.itmowidgets.ui.widgets

import android.content.Context
import me.alllexey123.itmowidgets.workers.LessonWidgetUpdateWorker
import me.alllexey123.itmowidgets.workers.QrWidgetUpdateWorker

object WidgetUtils {

    fun updateAllWidgets(context: Context) {
        QrWidgetUpdateWorker.enqueueImmediateUpdate(context)
        LessonWidgetUpdateWorker.enqueueImmediateUpdate(context)
    }
}