package me.alllexey123.itmowidgets.ui.widgets

class SingleLessonData(
    val subject: String = "", val times: String = "", val teacher: String = "",
    val workTypeId: Int = 0, val room: String = "", val building: String = "",
    val moreLessonsText: String = "",
    val hideTeacher: Boolean, val hideLocation: Boolean, val hideTime: Boolean, val hideMoreLessonsText: Boolean
)