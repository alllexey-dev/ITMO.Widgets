package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.ui.ConditionTone
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.fullDateText
import dev.alllexey.itmowidgets.core.ui.timeRangeText
import dev.alllexey.itmowidgets.databinding.ItemSportFriendsBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import kotlinx.datetime.format
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.util.Locale

private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")

/** `DateTimeFormatter` renders Russian weekday and month names in lower case. */
private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }

fun SportSessionTiming.timeText(): String = timeRangeText(start.time.toJavaLocalTime(), end.time.toJavaLocalTime())

fun SportSessionTiming.dateText(context: Context): String {
    val date = start.date.format(DateTexts.DAY_MONTH)
    return when {
        isToday -> context.getString(R.string.sport_date_today, date)
        isTomorrow -> context.getString(R.string.sport_date_tomorrow, date)
        else -> start.date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH).capitalizeFirst()
    }
}

/** Card date line: relative while the session is near, otherwise the weekday alone. */
fun SportSessionTiming.weekdayText(context: Context): String = when {
    isToday -> context.getString(R.string.sport_card_today)
    isTomorrow -> context.getString(R.string.sport_card_tomorrow)
    else -> start.date.format(DateTexts.WEEKDAY).capitalizeFirst()
}

fun SportSessionTiming.fullDateText(): String = fullDateText(start.date.toJavaLocalDate())

/** «вторник, 8 сентября, 18:30–20:00»: absolute, because the recipient reads it on another day. */
fun SportSessionTiming.shareDateText(): String =
    "${start.date.format(DateTexts.WEEKDAY_DAY_MONTH)}, ${timeText()}"

fun SportRegistrationStatus.label(context: Context): String = context.getString(when (this) {
    SportRegistrationStatus.SIGNED -> R.string.sport_status_signed
    SportRegistrationStatus.AUTO_SIGNED -> R.string.sport_status_auto_sign_success
    SportRegistrationStatus.WAITING -> R.string.sport_registration_waiting
    SportRegistrationStatus.NOTIFIED -> R.string.sport_registration_notified
    SportRegistrationStatus.FAILED -> R.string.sport_status_sign_failed
    SportRegistrationStatus.EXPIRED -> R.string.sport_registration_expired
    SportRegistrationStatus.CANCELLED -> R.string.sport_registration_cancelled
    SportRegistrationStatus.NOT_SIGNED -> R.string.sport_status_not_signed
})

/** Statuses that carry no outcome yet stay neutral rather than borrowing a semantic accent. */
fun SportRegistrationStatus.tone(): ConditionTone? = when (this) {
    SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> ConditionTone.ALLOWED
    SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> ConditionTone.WAITING
    SportRegistrationStatus.FAILED, SportRegistrationStatus.EXPIRED -> ConditionTone.BLOCKED
    SportRegistrationStatus.CANCELLED, SportRegistrationStatus.NOT_SIGNED -> null
}

fun bindSportStatus(status: SportRegistrationStatus, text: TextView, label: String = status.label(text.context)) {
    val accent = status.tone()?.accent(text.context) ?: text.context.color.onSurfaceVariant
    text.text = label
    text.setTextColor(accent)
    val iconRes = when (status) {
        SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> R.drawable.ic_check
        SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> R.drawable.ic_schedule
        SportRegistrationStatus.FAILED, SportRegistrationStatus.EXPIRED -> R.drawable.ic_error
        else -> R.drawable.ic_info
    }
    val icon = ContextCompat.getDrawable(text.context, iconRes)?.mutate()
    icon?.let { DrawableCompat.setTint(it, accent) }
    val size = (16 * text.resources.displayMetrics.density).toInt()
    icon?.setBounds(0, 0, size, size)
    text.setCompoundDrawablesRelative(icon, null, null, null)
}

fun ItemSportFriendsBinding.bind(friends: List<FriendSportBooking>) {
    root.isVisible = friends.isNotEmpty()
    listOf(avatar1, avatar2, avatar3).forEachIndexed { index, avatar ->
        val friend = friends.getOrNull(index)?.friend
        avatar.isVisible = friend != null
        avatar.setUser(friend)
    }
    friendsHint.text = root.context.getString(R.string.sport_friends_count_label, friends.size)
}
