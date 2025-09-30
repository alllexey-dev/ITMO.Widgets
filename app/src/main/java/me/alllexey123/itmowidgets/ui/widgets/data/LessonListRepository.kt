package me.alllexey123.itmowidgets.ui.widgets.data

object LessonListDataHolder {
    @Volatile
    private var data: LessonListWidgetData = LessonListWidgetData(
        listOf(LessonListWidgetEntry.Updating)
    )

    fun setData(newData: LessonListWidgetData) {
        data = newData
    }

    fun getData(): LessonListWidgetData = data
}