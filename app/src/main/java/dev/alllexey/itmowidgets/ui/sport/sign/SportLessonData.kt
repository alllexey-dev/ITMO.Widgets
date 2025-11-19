package dev.alllexey.itmowidgets.ui.sport.sign

import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue

data class SportLessonData(
    val apiData: SportLesson,
    val isReal: Boolean, // false means its predicted
    val unavailableReasons: List<UnavailableReason>,
    val canSignIn: Boolean,
    val freeSignStatus: SportFreeSignEntry?,
    val freeSignQueue: SportFreeSignQueue?,
    val autoSignStatus: SportAutoSignEntry?,
    val autoSignQueue: SportAutoSignQueue?,
)