package dev.alllexey.itmowidgets.ui.widgets.data

// null to hide
data class SingleLessonWidgetData(
    val subject: String = "",
    val times: String? = null,
    val teacher: String? = null,
    val workTypeId: Int = 0,
    val room: String? = null,
    val building: String? = null,
    val moreLessonsText: String? = null,
    val layoutId: Int
) {
    companion object {
        fun Error(layoutId: Int, errorMessage: String = "Ошибка загрузки данных"): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = errorMessage,
                workTypeId = 4,
                layoutId = layoutId
            )
        }

        fun FullDayEmpty(layoutId: Int, message: String = "Сегодня нет пар"): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = message,
                workTypeId = -1,
                layoutId = layoutId
            )
        }

        fun NoMoreLessons(layoutId: Int, message: String = "Сегодня больше нет пар"): SingleLessonWidgetData {
            return SingleLessonWidgetData(
                subject = message,
                workTypeId = -1,
                layoutId = layoutId
            )
        }
    }
}