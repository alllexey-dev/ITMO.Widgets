package dev.alllexey.itmowidgets.ui.sport.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.databinding.ItemSportLessonBinding

class SportLessonsAdapter(
    private val onSignUpClick: (SportLesson) -> Unit
) : ListAdapter<SportLesson, SportLessonsAdapter.LessonViewHolder>(LessonDiffCallback()) {

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

    inner class LessonViewHolder(private val binding: ItemSportLessonBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var lesson: SportLesson

        fun bind(lesson: SportLesson) {
            this.lesson = lesson
            binding.sectionNameTextView.text = lesson.sectionName
            binding.timeTextView.text = "${lesson.timeSlotStart}-${lesson.timeSlotEnd}"
            binding.teacherTextView.text = lesson.teacherFio

            binding.locationTextView.text = lesson.roomName

            val canSignIn = lesson.canSignIn.isCanSignIn
            val isSigned = lesson.signed ?: false

            if (isSigned) {
                binding.signUpButton.text = "Вы записаны"
                binding.signUpButton.isEnabled = false
                binding.statusChip.visibility = View.GONE
                binding.signUpButton.setOnClickListener(null)
                setMuted(false)
            } else if (canSignIn) {
                binding.signUpButton.text = "Записаться"
                binding.signUpButton.isEnabled = true
                binding.signUpButton.visibility = View.VISIBLE
                binding.statusChip.visibility = View.GONE
                binding.signUpButton.setOnClickListener {
                    onSignUpClick(lesson)
                }
                setMuted(false)
            } else {
                binding.signUpButton.setOnClickListener(null)
                binding.signUpButton.visibility = View.GONE
                binding.statusChip.visibility = View.VISIBLE

                when {
                    lesson.intersection == true -> {
                        binding.statusChip.text = "Пересечение"
                    }
                    (lesson.available ?: 0) <= 0 -> {
                        binding.statusChip.text = "Нет мест"
                    }
                    else -> {
                        binding.statusChip.text = lesson.canSignIn.unavailableReasons.firstOrNull() ?: "Недоступно"
                    }
                }
                setMuted(true)
            }
        }

        private fun setMuted(isMuted: Boolean) {
            val alpha = if (isMuted) 0.6f else 1.0f
            binding.sportLessonCardView.alpha = alpha
        }
    }
}

class LessonDiffCallback : DiffUtil.ItemCallback<SportLesson>() {
    override fun areItemsTheSame(oldItem: SportLesson, newItem: SportLesson): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SportLesson, newItem: SportLesson): Boolean {
        return oldItem == newItem
    }
}