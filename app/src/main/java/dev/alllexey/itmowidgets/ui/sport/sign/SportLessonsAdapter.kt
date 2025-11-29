package dev.alllexey.itmowidgets.ui.sport.sign

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.databinding.ItemSportLessonBinding
import dev.alllexey.itmowidgets.util.SportUtils

interface SportSignActionsListener {
    fun onSignUpClick(lesson: SportLessonData)
    fun onUnSignClick(lesson: SportLessonData)
    fun onAutoSignClick(lesson: SportLessonData)
    fun onUnAutoSignClick(lesson: SportLessonData)
}

class SportLessonsAdapter(val listener: SportSignActionsListener) :
    ListAdapter<SportLessonData, SportLessonsAdapter.LessonViewHolder>(LessonDiffCallback()) {

    private var buildingsMap: Map<Long, String> = emptyMap()

    fun setBuildingsMap(map: Map<Long, String>) {
        this.buildingsMap = map
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val binding = ItemSportLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LessonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LessonViewHolder(private val binding: ItemSportLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lessonData: SportLessonData) {
            val apiLesson = lessonData.apiData

            binding.sectionNameTextView.text = SportUtils.shortenSectionName(apiLesson.sectionName)
            binding.timeTextView.text = "${apiLesson.timeSlotStart}-${apiLesson.timeSlotEnd}"
            binding.teacherTextView.text = apiLesson.teacherFio
            binding.locationTextView.text = apiLesson.roomName
            binding.intersectionIndicator.isVisible = apiLesson.intersection == true

            bindBadges(lessonData)
            bindProgress(lessonData)
            bindActions(lessonData)
        }

        private fun bindBadges(lessonData: SportLessonData) {
            val apiLesson = lessonData.apiData
            val isFull = lessonData.isReal && (apiLesson.available ?: 0) <= 0 && apiLesson.signed != true

            binding.typeText.text = SportUtils.getSportLessonTypeName(lessonData.apiData)
            binding.predictionBadge.isVisible = !lessonData.isReal
            binding.fullBadge.isVisible = isFull
        }

        private fun bindProgress(lessonData: SportLessonData) {
            val apiLesson = lessonData.apiData
            if (lessonData.isReal) {
                val signed = apiLesson.limit - (apiLesson.available ?: 0)
                binding.signedUpTextView.text = "$signed / ${apiLesson.limit}"
                binding.occupancyProgress.max = apiLesson.limit.toInt()
                binding.occupancyProgress.progress = signed.toInt()

                binding.occupancyProgress.isVisible = true
                binding.signedUpLabel.isVisible = true
            } else {
                binding.occupancyProgress.isVisible = false
                binding.signedUpLabel.isVisible = false
                binding.signedUpTextView.text = ""
            }
        }

        private fun bindActions(lessonData: SportLessonData) {
            val apiLesson = lessonData.apiData
            val isSigned = apiLesson.signed == true
            val canSignIn = lessonData.canSignIn
            val reason = lessonData.unavailableReasons.lastOrNull()

            binding.statusChip.isVisible = false
            binding.signUpButton.isVisible = false
            binding.signUpButton.setOnClickListener(null)
            setMuted(false)

            when {
                isSigned && lessonData.isReal -> {
                    setupButton(
                        text = "Отписаться",
                        colorAttr = com.google.android.material.R.attr.colorErrorContainer,
                        textColorAttr = com.google.android.material.R.attr.colorOnErrorContainer,
                        onClick = { listener.onUnSignClick(lessonData) }
                    )
                }

                lessonData.isReal && canSignIn && (apiLesson.available ?: 0) > 0 -> {
                    setupButton(
                        text = "Записаться",
                        colorAttr = com.google.android.material.R.attr.colorSecondaryContainer,
                        textColorAttr = com.google.android.material.R.attr.colorOnSecondaryContainer,
                        onClick = { listener.onSignUpClick(lessonData) }
                    )
                }

                (lessonData.isReal && reason is UnavailableReason.Full) || (!lessonData.isReal && canSignIn) -> {
                    val status = if (lessonData.isReal) lessonData.freeSignStatus else lessonData.autoSignStatus
                    val position = if (lessonData.isReal) lessonData.freeSignStatus?.position else lessonData.autoSignStatus?.position
                    val queue = if (lessonData.isReal) lessonData.freeSignQueue else lessonData.autoSignQueue
                    val total = if (lessonData.isReal) lessonData.freeSignQueue?.total else lessonData.autoSignQueue?.total

                    val buttonText = if (status != null) {
                        "Автозапись (${position} / ${total})"
                    } else if (queue != null) {
                        "Автозапись (${total})"
                    } else {
                        "Автозапись"
                    }

                    setupButton(
                        text = buttonText,
                        colorAttr = com.google.android.material.R.attr.colorTertiaryContainer,
                        textColorAttr = com.google.android.material.R.attr.colorOnTertiaryContainer,
                        onClick = {
                            if (status != null) listener.onUnAutoSignClick(lessonData)
                            else listener.onAutoSignClick(lessonData)
                        }
                    )
                }

                else -> {
                    binding.statusChip.isVisible = true
                    binding.statusChip.text = reason?.shortDescription ?: "Недоступно"
                    setMuted(true)
                }
            }
        }

        private fun setupButton(text: String, @AttrRes colorAttr: Int, @AttrRes textColorAttr: Int, onClick: () -> Unit) {
            binding.signUpButton.isVisible = true
            binding.signUpButton.isEnabled = true
            binding.signUpButton.text = text
            binding.signUpButton.backgroundTintList = ColorStateList.valueOf(resolveColor(colorAttr))
            binding.signUpButton.setTextColor(resolveColor(textColorAttr))
            binding.signUpButton.setOnClickListener { onClick() }
        }

        private fun resolveColor(@AttrRes attr: Int): Int {
            return MaterialColors.getColor(itemView, attr, 0)
        }

        private fun setMuted(isMuted: Boolean) {
            binding.sportLessonCardView.alpha = if (isMuted) 0.6f else 1.0f
        }
    }
}

class LessonDiffCallback : DiffUtil.ItemCallback<SportLessonData>() {
    override fun areItemsTheSame(oldItem: SportLessonData, newItem: SportLessonData): Boolean {
        return oldItem.apiData.id == newItem.apiData.id
    }

    override fun areContentsTheSame(oldItem: SportLessonData, newItem: SportLessonData): Boolean {
        return oldItem == newItem
    }
}