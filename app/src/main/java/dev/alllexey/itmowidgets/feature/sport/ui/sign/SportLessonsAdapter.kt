package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.ThemeColors
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSportLessonBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason

interface SportSignActionsListener {
    fun onSignUpClick(lesson: SportLesson)
    fun onUnSignClick(lesson: SportLesson)
    fun onAutoSignClick(lesson: SportLesson)
    fun onUnAutoSignClick(lesson: SportLesson)
    fun onLessonClick(lesson: SportLesson)
}

class SportLessonsAdapter(val listener: SportSignActionsListener) :
    ListAdapter<SportLesson, SportLessonsAdapter.LessonViewHolder>(LessonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val binding = ItemSportLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LessonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LessonViewHolder(private val binding: ItemSportLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SportLesson) {
            binding.sectionNameTextView.text = item.sectionName.shorten()
            binding.timeTextView.text = "${item.start.toLocalTime()}-${item.end.toLocalTime()}"
            binding.teacherTextView.text = item.teacherFio
            binding.locationTextView.text = item.roomName
            binding.intersectionIndicator.isVisible = item.intersection == true

            binding.sportLessonCardView.setOnClickListener {
                listener.onLessonClick(item)
            }

            bindBadges(item)
            bindProgress(item)
            bindActions(item)
            setupFriends(item)
        }

        private fun bindBadges(item: SportLesson) {
            val isFull = item.isLessonReal && item.available <= 0 && !item.signed

            binding.typeText.setText(item.kind.titleRes())
            binding.predictionBadge.isVisible = !item.isLessonReal
            binding.fullBadge.isVisible = isFull
        }

        private fun bindProgress(item: SportLesson) {
            with(binding) {
                if (item.isLessonReal) {
                    val signed = item.limit - item.available
                    signedUpTextView.text = "$signed / ${item.limit}"
                    occupancyProgress.max = item.limit
                    occupancyProgress.progress = signed

                    occupancyProgress.isVisible = true
                    signedUpLabel.isVisible = true
                } else {
                    occupancyProgress.isVisible = false
                    signedUpLabel.isVisible = false
                    signedUpTextView.text = ""
                }
            }
        }

        private fun bindActions(item: SportLesson) {
            val canSignIn = item.canSignIn
            val reason = item.unavailableReasons.lastOrNull()

            with(binding) {
                statusChip.isVisible = false
                signUpButton.isVisible = false
                signUpButton.setOnClickListener(null)
                setMuted(false)

                when {
                    item.signed && item.isLessonReal -> {
                        setupButton(
                            text = root.context.getString(R.string.sport_lesson_sign_out),
                            colorAttr = MaterialR.attr.colorErrorContainer,
                            textColorAttr = MaterialR.attr.colorOnErrorContainer,
                            onClick = { listener.onUnSignClick(item) }
                        )
                    }

                    item.isLessonReal && canSignIn && item.available > 0 -> {
                        setupButton(
                            text = root.context.getString(R.string.sport_lesson_sign_up),
                            colorAttr = MaterialR.attr.colorSecondaryContainer,
                            textColorAttr = MaterialR.attr.colorOnSecondaryContainer,
                            onClick = { listener.onSignUpClick(item) }
                        )
                    }

                    (item.isLessonReal && reason is UnavailableReason.Full) || (!item.isLessonReal && (reason is UnavailableReason.Full || reason == null)) -> {
                        val entry = item.signEntry
                        val queue = item.signQueue
                        val buttonText = if (entry != null) {
                            root.context.getString(
                                R.string.sport_auto_sign_position,
                                entry.position,
                                entry.total
                            )
                        } else if (queue != null) {
                            root.context.getString(
                                R.string.sport_auto_sign_queue_size,
                                queue.total
                            )
                        } else {
                            root.context.getString(R.string.sport_auto_sign_title)
                        }

                        setupButton(
                            text = buttonText,
                            colorAttr = MaterialR.attr.colorTertiaryContainer,
                            textColorAttr = MaterialR.attr.colorOnTertiaryContainer,
                            onClick = {
                                if (entry != null) listener.onUnAutoSignClick(item)
                                else listener.onAutoSignClick(item)
                            }
                        )
                    }

                    else -> {
                        statusChip.isVisible = true
                        statusChip.text = reason?.shortDescription
                            ?: root.context.getString(R.string.sport_lesson_unavailable)
                        setMuted(true)
                    }
                }
            }
        }

        private fun setupButton(text: String, @AttrRes colorAttr: Int, @AttrRes textColorAttr: Int, onClick: () -> Unit) {
            with(binding) {
                signUpButton.isVisible = true
                signUpButton.isEnabled = true
                signUpButton.text = text
                signUpButton.backgroundTintList = ColorStateList.valueOf(color.resolve(colorAttr))
                signUpButton.setTextColor(color.resolve(textColorAttr))
                signUpButton.setOnClickListener { onClick() }
            }
        }

        private fun setupFriends(item: SportLesson) {
            with(binding) {
                val friends = item.friendsBookings

                friendsLayout.visibility = View.GONE
                avatar1.visibility = View.GONE
                avatar2.visibility = View.GONE
                avatar3.visibility = View.GONE
                moreFriendsText.visibility = View.GONE

                if (friends.isNotEmpty()) {
                    friendsLayout.visibility = View.VISIBLE

                    val visibleFriends = friends.take(3)

                    avatar2.translationX = -12f
                    avatar3.translationX = -24f
                    moreFriendsText.translationX = -36f

                    if (visibleFriends.size >= 1) {
                        avatar1.visibility = View.VISIBLE
                        avatar1.setUser(visibleFriends[0].friend)
                    }

                    if (visibleFriends.size >= 2) {
                        avatar2.visibility = View.VISIBLE
                        avatar2.setUser(visibleFriends[1].friend)
                    }

                    if (visibleFriends.size >= 3) {
                        avatar3.visibility = View.VISIBLE
                        avatar3.setUser(visibleFriends[2].friend)
                    }

                    if (friends.size > 3) {
                        moreFriendsText.visibility = View.VISIBLE
                        moreFriendsText.text = "+${friends.size - 3}"
                    }

                    val names = visibleFriends.joinToString(", ") {
                        it.friend.name.substringBefore(" ")
                    }

                    friendsHint.text =
                        if (friends.size > 3) {
                            root.context.getString(R.string.sport_friends_more, names)
                        } else {
                            names
                        }
                }
            }
        }

        val color: ThemeColors get() = binding.root.context.color

        private fun setMuted(isMuted: Boolean) {
            binding.sportLessonCardView.alpha = if (isMuted) 0.6f else 1.0f
        }
    }
}

class LessonDiffCallback : DiffUtil.ItemCallback<SportLesson>() {
    override fun areItemsTheSame(oldItem: SportLesson, newItem: SportLesson): Boolean {
        return oldItem.lessonId == newItem.lessonId
    }

    override fun areContentsTheSame(oldItem: SportLesson, newItem: SportLesson): Boolean {
        return oldItem == newItem
    }
}
