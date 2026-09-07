package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.ThemeColors
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSportLessonBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.ui.common.titleRes
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportConditionTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.occupancyTone
import dev.alllexey.itmowidgets.feature.sport.ui.common.bind
import dev.alllexey.itmowidgets.feature.sport.ui.common.timeText

interface SportSignActionsListener {
    fun onSignUpClick(lesson: SportLesson)
    fun onUnSignClick(lesson: SportLesson)
    fun onAutoSignClick(lesson: SportLesson)
    fun onUnAutoSignClick(lesson: SportLesson)
    fun onLessonClick(lesson: SportLesson)
}

/** Lesson plus the transient UI state that does not belong to the domain model. */
data class SportLessonItem(
    val lesson: SportLesson,
    val isBusy: Boolean = false
)

class SportLessonsAdapter(val listener: SportSignActionsListener, private val timeProvider: AcademicTimeProvider) :
    ListAdapter<SportLessonItem, SportLessonsAdapter.LessonViewHolder>(LessonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val binding = ItemSportLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LessonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item.lesson, item.isBusy)
    }

    inner class LessonViewHolder(private val binding: ItemSportLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SportLesson, isBusy: Boolean) {
            binding.sectionNameTextView.text = item.sectionName.shorten()
            binding.timeTextView.text = SportSessionTiming(item.start, item.end, timeProvider).timeText()
            binding.sectionNameTextView.setTextColor(binding.root.context.color.onSurface)
            binding.teacherTextView.text = item.teacherFio
            binding.locationTextView.text = item.roomName
            binding.intersectionIndicator.isVisible = item.intersection
            binding.intersectionIndicator.imageTintList = ColorStateList.valueOf(SportConditionTone.WARNING.accent(itemView.context))
            binding.teacherRow.isVisible = item.teacherFio.isNotBlank()
            binding.locationRow.isVisible = item.roomName.isNotBlank()

            binding.sportLessonCardView.setOnClickListener {
                listener.onLessonClick(item)
            }

            bindBadges(item)
            bindProgress(item)
            bindActions(item, isBusy)
            with(binding) {
                // The 48 dp touch target contains a vertically inset outline. Align that visible
                // outline with the card's end padding, not the button's invisible touch bounds.
                val buttonInset = if (signUpButton.isVisible) signUpButton.insetBottom else 0
                lessonCardContent.updatePadding(bottom = lessonCardContent.paddingEnd - buttonInset)
            }
            binding.friendsPreview.bind(item.friendsBookings)
        }

        private fun bindBadges(item: SportLesson) {
            val kind = binding.root.context.getString(item.kind.compactTitleRes())
            binding.typeText.text = kind
            binding.typeText.contentDescription = binding.root.context.getString(item.kind.titleRes())
        }

        private fun bindProgress(item: SportLesson) = with(binding) {
            val occupancy = SportOccupancy.from(item.isLessonReal, item.available, item.limit)
            signedUpTextView.isVisible = occupancy != null
            occupancyProgress.isVisible = occupancy != null
            occupancyProgress.max = occupancy?.limit ?: 1
            occupancyProgress.setProgressCompat(occupancy?.occupied ?: 0, false)
            occupancy?.let {
                val tone = occupancyTone(it.available, it.limit)
                occupancyProgress.setIndicatorColor(tone.accent(root.context))
                signedUpTextView.text = root.context.getString(R.string.sport_capacity_card, it.occupied, it.limit)
                // Only a scarce or full lesson is worth an accent; a roomy one stays quiet.
                signedUpTextView.setTextColor(
                    if (tone == SportConditionTone.WAITING) color.onSurfaceVariant else tone.accent(root.context)
                )
            }
        }

        private fun bindActions(item: SportLesson, isBusy: Boolean) {
            val availability = item.bookingConditions().evaluate(timeProvider.now())
            with(binding) {
                statusText.isVisible = false
                statusText.setTextColor(root.context.color.onSurfaceVariant)
                signUpButton.isVisible = false
                signUpButton.setOnClickListener(null)
                val action = availability.action
                if (action == SportBookingAction.NONE) {
                    statusText.isVisible = true
                    statusText.text = availability.restrictions.firstOrNull()?.let {
                        it.detail ?: root.context.getString(it.kind.titleRes())
                    } ?: root.context.getString(R.string.sport_lesson_unavailable)
                    statusText.setTextColor(SportConditionTone.BLOCKED.accent(root.context))
                    return
                }
                val label = when (action) {
                    SportBookingAction.SIGN -> R.string.sport_lesson_sign_up
                    SportBookingAction.CANCEL -> R.string.sport_lesson_sign_out
                    SportBookingAction.AUTO -> R.string.sport_auto_sign_title
                    SportBookingAction.CANCEL_AUTO -> R.string.sport_card_cancel_auto
                    SportBookingAction.NONE -> error("Handled above")
                }
                if (action == SportBookingAction.CANCEL_AUTO || !item.isLessonReal) {
                    statusText.isVisible = true
                    if (action == SportBookingAction.CANCEL_AUTO) {
                        statusText.text = item.signEntry?.let {
                            root.context.getString(R.string.sport_card_queue, it.position, it.total)
                        }
                        statusText.setTextColor(SportConditionTone.WAITING.accent(root.context))
                    } else {
                        statusText.text = root.context.getString(R.string.sport_lesson_prediction)
                    }
                }
                setupButton(
                    text = root.context.getString(label),
                    tone = when (action) {
                        SportBookingAction.SIGN -> SportConditionTone.ALLOWED
                        SportBookingAction.AUTO, SportBookingAction.CANCEL_AUTO -> SportConditionTone.WAITING
                        else -> null
                    },
                    onClick = {
                        if (item.bookingConditions().evaluate(timeProvider.now()).action != action) {
                            bind(item, false)
                        } else when (action) {
                            SportBookingAction.SIGN -> listener.onSignUpClick(item)
                            SportBookingAction.CANCEL -> listener.onUnSignClick(item)
                            SportBookingAction.AUTO -> listener.onAutoSignClick(item)
                            SportBookingAction.CANCEL_AUTO -> listener.onUnAutoSignClick(item)
                            SportBookingAction.NONE -> Unit
                        }
                    },
                    isBusy = isBusy
                )
            }
        }

        private fun setupButton(
            text: String,
            tone: SportConditionTone?,
            onClick: () -> Unit,
            isBusy: Boolean
        ) {
            with(binding) {
                signUpButton.isVisible = true
                signUpButton.isEnabled = !isBusy
                signUpButton.text = text
                val accent = tone?.accent(root.context) ?: color.onSurfaceVariant
                signUpButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                signUpButton.strokeWidth = resourcesDensityPixel()
                signUpButton.strokeColor = ColorStateList.valueOf(accent)
                signUpButton.setTextColor(accent)
                signUpButton.icon = if (isBusy) busyIndicator(accent) else null
                signUpButton.setOnClickListener(if (isBusy) null else View.OnClickListener { onClick() })
            }
        }

        private fun resourcesDensityPixel(): Int = itemView.resources.displayMetrics.density.toInt().coerceAtLeast(1)

        /** Material's own indeterminate drawable, tinted like the button label. */
        private fun busyIndicator(accent: Int): Drawable {
            val context = itemView.context
            val spec = CircularProgressIndicatorSpec(
                context,
                null,
                0,
                com.google.android.material.R.style
                    .Widget_Material3_CircularProgressIndicator_ExtraSmall
            )
            spec.indicatorColors = intArrayOf(accent)
            return IndeterminateDrawable.createCircularDrawable(context, spec)
        }

        val color: ThemeColors get() = binding.root.context.color

    }
}

class LessonDiffCallback : DiffUtil.ItemCallback<SportLessonItem>() {
    override fun areItemsTheSame(oldItem: SportLessonItem, newItem: SportLessonItem): Boolean {
        return oldItem.lesson.lessonId == newItem.lesson.lessonId
    }

    override fun areContentsTheSame(oldItem: SportLessonItem, newItem: SportLessonItem): Boolean {
        return oldItem == newItem
    }
}
