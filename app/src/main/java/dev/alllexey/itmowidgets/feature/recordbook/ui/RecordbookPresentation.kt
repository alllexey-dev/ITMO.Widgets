package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

fun RecordbookSubject.displayRate(context: Context): String {
    return when (val value = normalizedRate) {
        RecordbookRate.Credit -> context.getString(R.string.recordbook_rate_credit)
        RecordbookRate.InProgress -> context.getString(R.string.recordbook_rate_in_progress)
        is RecordbookRate.Grade -> value.code
    }
}
