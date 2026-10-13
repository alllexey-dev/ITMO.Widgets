package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.formatRecordbookNumber
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings

/** What the bell and the notification say about one subject's news, with the numbers when the source gave them. */
object MarkNewsTexts {

    /** `Матанализ: контрольная работа 2` for a BARS checkpoint, the subject alone otherwise. */
    fun title(news: MarkNews): UiText {
        val checkpoint = news.value?.checkpoint ?: return UiText.Dynamic(news.name)
        return UiText.Res(RecordbookStrings.recordbook_news_title, listOf(news.name, UiText.LowercaseFirst(UiText.Dynamic(checkpoint))))
    }

    /** `9 из 10, итого 74`, `Неявка`, `Оценка 5, итого 91`, `Итого 74`; `Новые баллы` without numbers. */
    fun details(news: MarkNews): UiText {
        val value = news.value ?: return UiText.Res(RecordbookStrings.recordbook_news_without_value)
        val head = headline(value)
        val total = value.total?.let {
            UiText.Res(RecordbookStrings.recordbook_news_total, listOf(formatRecordbookNumber(it)))
        }
        val parts = listOfNotNull(head, total?.let { if (head == null) it else UiText.LowercaseFirst(it) })
        return when (parts.size) {
            0 -> UiText.Res(RecordbookStrings.recordbook_news_without_value)
            1 -> parts.single()
            else -> UiText.Joined(parts, SEPARATOR)
        }
    }

    /** `Матанализ: 9 из 10` for a notification about one subject; null without a number to name. */
    fun notificationTitle(news: MarkNews): UiText? {
        val value = news.value ?: return null
        val said = headline(value)
            ?: value.total?.let { UiText.Res(RecordbookStrings.recordbook_news_total, listOf(formatRecordbookNumber(it))) }
            ?: return null
        return UiText.Res(RecordbookStrings.recordbook_news_title, listOf(news.name, UiText.LowercaseFirst(said)))
    }

    /** `+9`, `−3,5`: the change of the points; null without one or for no change. */
    fun delta(value: MarkValue?): String? {
        val delta = value?.delta?.takeIf { it != 0.0 } ?: return null
        return if (delta > 0) "+${formatRecordbookNumber(delta)}" else MINUS + formatRecordbookNumber(-delta)
    }

    /** The mark out of its maximum, an absence or a grade: the first thing worth saying. */
    private fun headline(value: MarkValue): UiText? = when {
        value.absent -> UiText.Res(RecordbookStrings.recordbook_news_absent)
        value.grade != null -> UiText.Res(RecordbookStrings.recordbook_news_grade, listOf(value.grade))
        value.mark != null && value.max != null -> UiText.Res(
            RecordbookStrings.recordbook_news_mark,
            listOf(formatRecordbookNumber(value.mark), formatRecordbookNumber(value.max)),
        )
        value.mark != null -> UiText.Dynamic(formatRecordbookNumber(value.mark))
        else -> null
    }

    private const val SEPARATOR = ", "
    private const val MINUS = "\u2212"
}
