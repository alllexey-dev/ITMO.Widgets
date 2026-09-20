package dev.alllexey.itmowidgets.feature.home.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.home.QrPass
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.qr.QrPassImages
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.ui.buildingShortTitle
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.roomShortTitle
import dev.alllexey.itmowidgets.databinding.ItemHomeFriendRequestsBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeHintBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeLessonRowBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeQrBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeScheduleBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeSportBinding
import dev.alllexey.itmowidgets.databinding.ItemHomeSportRowBinding
import dev.alllexey.itmowidgets.databinding.ItemUserRowBinding
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Everything a card can ask the screen to do; navigation stays in the Fragment. */
data class HomeFeedActions(
    val onLesson: (LessonDetailsArgs) -> Unit = {},
    val onPendingSport: (PendingSportDetailsArgs) -> Unit = {},
    val onOpenQr: () -> Unit = {},
    val onOpenSport: () -> Unit = {},
    val onOpenFriends: () -> Unit = {},
    val onOpenUser: (UserSummary) -> Unit = {},
    val onHint: (HomeHint) -> Unit = {},
    val onDismissHint: (HomeHint) -> Unit = {}
)

/**
 * One view type per card kind. Rows inside a card are plain inflated views:
 * a card never holds more than a handful, and nested lists would only add
 * scroll conflicts.
 */
class HomeFeedAdapter(
    private val actions: HomeFeedActions,
    private val qrImages: QrPassImages,
    private val scope: CoroutineScope,
    private val zoneId: ZoneId
) : ListAdapter<HomeCard, RecyclerView.ViewHolder>(Diff) {

    private val qrCache = HashMap<String, Bitmap>()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    fun submitCards(cards: List<HomeCard>, onCommitted: () -> Unit) = submitList(cards, onCommitted)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HomeCard.Schedule -> TYPE_SCHEDULE
        is HomeCard.Qr -> TYPE_QR
        is HomeCard.Sport -> TYPE_SPORT
        is HomeCard.FriendRequests -> TYPE_FRIENDS
        is HomeCard.Hint -> TYPE_HINT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SCHEDULE -> ScheduleHolder(ItemHomeScheduleBinding.inflate(inflater, parent, false))
            TYPE_QR -> QrHolder(ItemHomeQrBinding.inflate(inflater, parent, false))
            TYPE_SPORT -> SportHolder(ItemHomeSportBinding.inflate(inflater, parent, false))
            TYPE_FRIENDS -> FriendsHolder(ItemHomeFriendRequestsBinding.inflate(inflater, parent, false))
            else -> HintHolder(ItemHomeHintBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val card = getItem(position)) {
            is HomeCard.Schedule -> (holder as ScheduleHolder).bind(card)
            is HomeCard.Qr -> (holder as QrHolder).bind(card)
            is HomeCard.Sport -> (holder as SportHolder).bind(card)
            is HomeCard.FriendRequests -> (holder as FriendsHolder).bind(card)
            is HomeCard.Hint -> (holder as HintHolder).bind(card)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? QrHolder)?.cancel()
    }

    inner class ScheduleHolder(private val binding: ItemHomeScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: HomeCard.Schedule) {
            val context = binding.root.context
            binding.scheduleTitle.text = context.getString(
                if (card.tomorrow) R.string.home_schedule_tomorrow else R.string.home_schedule_today
            )
            binding.scheduleDate.text = card.date.format(DATE_FORMATTER)
            binding.scheduleRows.removeAllViews()
            card.rows.forEach { row -> binding.scheduleRows.addView(rowView(binding.scheduleRows, row)) }
            binding.scheduleRows.isVisible = card.rows.isNotEmpty()
            val footer = when {
                card.rows.isEmpty() && card.completed == 0 -> context.getString(R.string.home_schedule_empty)
                card.rows.isEmpty() -> context.getString(R.string.home_schedule_done)
                card.completed > 0 && !card.tomorrow ->
                    context.resources.getQuantityString(R.plurals.home_schedule_completed, card.completed, card.completed)
                else -> null
            }
            binding.scheduleFooter.text = footer
            binding.scheduleFooter.isVisible = footer != null
        }

        private fun rowView(parent: ViewGroup, row: HomeScheduleRow): View {
            val context = parent.context
            val binding = ItemHomeLessonRowBinding.inflate(LayoutInflater.from(context), parent, false)
            when (row) {
                is HomeScheduleRow.Lesson -> {
                    val args = row.args
                    binding.rowTimeStart.text = LocalTime.parse(args.start).format(TIME_FORMATTER)
                    binding.rowTimeEnd.text = LocalTime.parse(args.end).format(TIME_FORMATTER)
                    binding.rowTitle.text = args.subjectName
                    binding.rowSubtitle.text = listOfNotNull(
                        context.getString(lessonTypeNameRes(args.typeId)),
                        args.room?.let { roomShortTitle(context, it) },
                        args.building?.let { buildingShortTitle(context, it) }
                    ).joinToString(SEPARATOR)
                    binding.rowTypeBar.backgroundTintList =
                        ContextCompat.getColorStateList(context, lessonTypeColorRes(args.typeId))
                    val badge = when (row.state) {
                        HomeLessonState.CURRENT -> R.string.home_schedule_now
                        HomeLessonState.NEXT -> R.string.home_schedule_next
                        HomeLessonState.UPCOMING -> null
                    }
                    binding.rowBadge.isVisible = badge != null
                    badge?.let(binding.rowBadge::setText)
                    styleFocus(binding, focused = row.state == HomeLessonState.CURRENT)
                    binding.rowRoot.setOnClickListener { actions.onLesson(args) }
                }
                is HomeScheduleRow.PendingSport -> {
                    val args = row.args
                    val start = OffsetDateTime.parse(args.start).atZoneSameInstant(zoneId)
                    val end = OffsetDateTime.parse(args.end).atZoneSameInstant(zoneId)
                    binding.rowTimeStart.text = start.format(TIME_FORMATTER)
                    binding.rowTimeEnd.text = end.format(TIME_FORMATTER)
                    binding.rowTitle.text = args.sectionName
                    binding.rowSubtitle.text = listOf(context.getString(R.string.title_sport), args.roomName)
                        .filter { it.isNotBlank() }.joinToString(SEPARATOR)
                    binding.rowTypeBar.backgroundTintList =
                        ContextCompat.getColorStateList(context, lessonTypeColorRes(SPORT_TYPE_ID))
                    binding.rowBadge.isVisible = true
                    binding.rowBadge.setText(if (row.predicted) R.string.home_pending_predicted else R.string.home_pending_waiting)
                    styleFocus(binding, focused = false)
                    binding.rowRoot.setOnClickListener { actions.onPendingSport(args) }
                }
            }
            return binding.root
        }

        /** The lesson in progress sits on the secondary container; everything on it follows that palette. */
        private fun styleFocus(binding: ItemHomeLessonRowBinding, focused: Boolean) {
            val root = binding.rowRoot
            root.setBackgroundResource(if (focused) R.drawable.bg_home_row_focus else R.drawable.bg_home_row_plain)
            val primary = MaterialColors.getColor(root, if (focused) com.google.android.material.R.attr.colorOnSecondaryContainer else com.google.android.material.R.attr.colorOnSurface)
            val secondary = MaterialColors.getColor(root, if (focused) com.google.android.material.R.attr.colorOnSecondaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.rowTimeStart.setTextColor(primary)
            binding.rowTitle.setTextColor(primary)
            binding.rowTimeEnd.setTextColor(secondary)
            binding.rowSubtitle.setTextColor(secondary)
            binding.rowBadge.backgroundTintList = ColorStateList.valueOf(
                MaterialColors.getColor(root, if (focused) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorSecondaryContainer)
            )
            binding.rowBadge.setTextColor(
                MaterialColors.getColor(root, if (focused) com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
        }
    }

    inner class QrHolder(private val binding: ItemHomeQrBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null
        private var shown: String? = null

        fun bind(card: HomeCard.Qr) {
            binding.homeQrCard.setOnClickListener { actions.onOpenQr() }
            binding.homeQrOpen.setOnClickListener { actions.onOpenQr() }
            val pass = card.pass
            binding.homeQrOpen.isVisible = pass == null
            binding.homeQrPlaceholder.isVisible = pass == null
            binding.homeQrImage.isVisible = pass != null
            binding.homeQrHint.setText(if (pass == null) R.string.home_qr_unavailable else R.string.home_qr_hint)
            if (pass == null) {
                cancel()
                binding.homeQrImage.setImageBitmap(null)
                return
            }
            render(pass)
        }

        private fun render(pass: QrPass) {
            val key = if (pass.spoiler) SPOILER_KEY else pass.hex
            if (key == shown) return
            cancel()
            qrCache[key]?.let {
                binding.homeQrImage.setImageBitmap(it)
                shown = key
                return
            }
            job = scope.launch {
                val bitmap = if (pass.spoiler) qrImages.spoiler() else qrImages.qr(pass.hex)
                qrCache[key] = bitmap
                binding.homeQrImage.setImageBitmap(bitmap)
                shown = key
            }
        }

        fun cancel() {
            job?.cancel()
            job = null
            shown = null
        }
    }

    inner class SportHolder(private val binding: ItemHomeSportBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: HomeCard.Sport) {
            val context = binding.root.context
            binding.homeSportCard.setOnClickListener { actions.onOpenSport() }
            val score = card.score
            binding.sportScore.isVisible = score != null
            binding.sportProgress.isVisible = score != null
            binding.sportRemaining.isVisible = score != null
            if (score != null) {
                binding.sportScore.text = context.getString(R.string.home_sport_score, score.totalCapped)
                binding.sportProgress.progress = score.totalCapped
                binding.sportRemaining.text =
                    context.resources.getQuantityString(R.plurals.sport_score_remaining_status, score.remaining, score.remaining)
            }
            binding.sportQueue.removeAllViews()
            card.queue.take(QUEUE_LIMIT).forEach { booking ->
                binding.sportQueue.addView(queueRow(binding.sportQueue, booking))
            }
            binding.sportQueue.isVisible = card.queue.isNotEmpty()
            val more = card.queue.size - QUEUE_LIMIT
            binding.sportMore.isVisible = more > 0
            if (more > 0) binding.sportMore.text = context.resources.getQuantityString(R.plurals.home_sport_more, more, more)
        }

        private fun queueRow(parent: ViewGroup, booking: PendingSportBooking): View {
            val context = parent.context
            val binding = ItemHomeSportRowBinding.inflate(LayoutInflater.from(context), parent, false)
            val start = booking.start.atZoneSameInstant(zoneId)
            val end = booking.end.atZoneSameInstant(zoneId)
            binding.rowTitle.text = booking.sectionName
            binding.rowSubtitle.text = "${start.format(SHORT_DATE_FORMATTER)}$SEPARATOR${start.format(TIME_FORMATTER)}–${end.format(TIME_FORMATTER)}"
            binding.rowBadge.setText(if (booking.isPrediction) R.string.home_pending_predicted else R.string.home_pending_waiting)
            binding.rowRoot.setOnClickListener { actions.onPendingSport(booking.toDetailsArgs()) }
            return binding.root
        }
    }

    inner class FriendsHolder(private val binding: ItemHomeFriendRequestsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: HomeCard.FriendRequests) {
            binding.friendsCount.text = card.incoming.size.toString()
            binding.friendsAll.setOnClickListener { actions.onOpenFriends() }
            binding.friendsRows.removeAllViews()
            card.incoming.take(FRIENDS_LIMIT).forEach { user ->
                val row = ItemUserRowBinding.inflate(LayoutInflater.from(binding.root.context), binding.friendsRows, false)
                row.avatar.setUser(user)
                row.name.text = user.name
                val group = user.groups.firstOrNull()?.name
                row.subtitle.isVisible = !group.isNullOrBlank()
                row.subtitle.text = group
                row.trailingIcon.isVisible = true
                row.root.setOnClickListener { actions.onOpenUser(user) }
                binding.friendsRows.addView(row.root)
            }
        }
    }

    inner class HintHolder(private val binding: ItemHomeHintBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: HomeCard.Hint) {
            val hint = card.hint
            val (icon, title, text, action) = when (hint) {
                HomeHint.WIDGETS -> HintContent(
                    R.drawable.ic_widgets, R.string.home_hint_widgets_title,
                    R.string.home_hint_widgets_description, R.string.home_hint_widgets_action
                )
                HomeHint.NOTIFICATIONS -> HintContent(
                    R.drawable.ic_notification, R.string.home_hint_notifications_title,
                    R.string.home_hint_notifications_description, R.string.home_hint_notifications_action
                )
                HomeHint.SERVICES -> HintContent(
                    R.drawable.ic_star_shine, R.string.home_hint_services_title,
                    R.string.home_hint_services_description, R.string.home_hint_services_action
                )
            }
            binding.hintIcon.setImageResource(icon)
            binding.hintTitle.setText(title)
            binding.hintText.setText(text)
            binding.hintAction.setText(action)
            binding.hintAction.setOnClickListener { actions.onHint(hint) }
            binding.hintDismiss.setOnClickListener { actions.onDismissHint(hint) }
        }
    }

    private data class HintContent(val icon: Int, val title: Int, val text: Int, val action: Int)

    private object Diff : DiffUtil.ItemCallback<HomeCard>() {
        override fun areItemsTheSame(oldItem: HomeCard, newItem: HomeCard) = oldItem.kind == newItem.kind
        override fun areContentsTheSame(oldItem: HomeCard, newItem: HomeCard) = oldItem == newItem
    }

    private companion object {
        const val TYPE_SCHEDULE = 1
        const val TYPE_QR = 2
        const val TYPE_SPORT = 3
        const val TYPE_FRIENDS = 4
        const val TYPE_HINT = 5
        const val QUEUE_LIMIT = 3
        const val FRIENDS_LIMIT = 3
        const val SPORT_TYPE_ID = 11
        const val SEPARATOR = " · "
        const val SPOILER_KEY = "spoiler"
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", RUSSIAN)
        val SHORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", RUSSIAN)
    }
}
