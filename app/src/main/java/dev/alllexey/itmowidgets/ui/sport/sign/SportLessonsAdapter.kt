package dev.alllexey.itmowidgets.ui.sport.sign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
        val binding =
            ItemSportLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LessonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LessonViewHolder(private val binding: ItemSportLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var lessonData: SportLessonData

        fun bind(lessonData: SportLessonData) {
            this.lessonData = lessonData
            val apiLesson = lessonData.apiData
            binding.sectionNameTextView.text = SportUtils.shortenSectionName(apiLesson.sectionName)
            binding.timeTextView.text = "${apiLesson.timeSlotStart}-${apiLesson.timeSlotEnd}"
            binding.teacherTextView.text = apiLesson.teacherFio

            binding.locationTextView.text = apiLesson.roomName
            val reason = lessonData.unavailableReasons.lastOrNull()

            val canSignIn = lessonData.canSignIn
            val isSigned = apiLesson.signed ?: false
            if (lessonData.apiData.intersection) {
                binding.intersectionIcon.visibility = View.VISIBLE
            } else {
                binding.intersectionIcon.visibility = View.GONE
            }
            if (lessonData.isReal) {
                binding.signedUpTextView.text =
                    "Записались: ${apiLesson.limit - apiLesson.available} из ${apiLesson.limit}"
                if (isSigned) {
                    binding.signUpButton.text = "Отписаться"
                    binding.signUpButton.visibility = View.VISIBLE
                    binding.signUpButton.isEnabled = true
                    binding.statusChip.visibility = View.GONE
                    binding.signUpButton.setOnClickListener {
                        listener.onUnSignClick(lessonData)
                    }
                    setMuted(false)
                } else if (canSignIn && apiLesson.available > 0) {
                    binding.signUpButton.text = "Записаться"
                    binding.signUpButton.isEnabled = true
                    binding.signUpButton.visibility = View.VISIBLE
                    if (lessonData.apiData.intersection ?: false) {
                        binding.statusChip.text = "Пересечение"
                        binding.statusChip.visibility = View.VISIBLE
                    } else {
                        binding.statusChip.visibility = View.GONE
                    }
                    binding.signUpButton.setOnClickListener {
                        listener.onSignUpClick(lessonData)
                    }
                    setMuted(false)
                } else {
                    binding.signUpButton.setOnClickListener(null)
                    binding.statusChip.visibility = View.VISIBLE
                    binding.statusChip.text = reason?.shortDescription

                    if (reason is UnavailableReason.Full) {
                        val freeSignQueue = lessonData.freeSignQueue
                        val freeSignStatus = lessonData.freeSignStatus
                        if (freeSignQueue != null) {
                            if (freeSignStatus != null) {
                                binding.signUpButton.visibility = View.VISIBLE
                                binding.signUpButton.text =
                                    "Автозапись (${freeSignStatus.position} / ${freeSignStatus.total})"
                                binding.signUpButton.setOnClickListener {
                                    listener.onUnAutoSignClick(lessonData)
                                }
                            } else {
                                binding.signUpButton.visibility = View.VISIBLE
                                binding.signUpButton.text = "Автозапись (${freeSignQueue.total})"
                                binding.signUpButton.setOnClickListener {
                                    listener.onAutoSignClick(lessonData)
                                }
                            }
                        } else {
                            binding.signUpButton.visibility = View.VISIBLE
                            binding.signUpButton.text = "Автозапись"
                            binding.signUpButton.setOnClickListener {
                                listener.onAutoSignClick(lessonData)
                            }
                        }
                        setMuted(false)
                    } else {
                        binding.signUpButton.visibility = View.GONE
                        setMuted(true)
                    }
                }
            } else {
                binding.signedUpTextView.text =
                    "Прогнозируемое занятие"
                if (canSignIn) {
                    binding.signUpButton.text = "Автозапись"
                    binding.signUpButton.isEnabled = true
                    binding.signUpButton.visibility = View.VISIBLE
                    binding.signUpButton.setOnClickListener {
                        listener.onAutoSignClick(lessonData)
                    }

                    val autoSignQueue = lessonData.autoSignQueue
                    val autoSignStatus = lessonData.autoSignStatus
                    if (autoSignQueue != null) {
                        if (autoSignStatus != null) {
                            binding.signUpButton.visibility = View.VISIBLE
                            binding.signUpButton.text =
                                "Автозапись (${autoSignStatus.position} / ${autoSignStatus.total})"
                            binding.signUpButton.setOnClickListener {
                                listener.onUnAutoSignClick(lessonData)
                            }
                        } else {
                            binding.signUpButton.visibility = View.VISIBLE
                            binding.signUpButton.text = "Автозапись (${autoSignQueue.total})"
                            binding.signUpButton.setOnClickListener {
                                listener.onAutoSignClick(lessonData)
                            }
                        }
                    } else {
                        binding.signUpButton.visibility = View.VISIBLE
                        binding.signUpButton.text = "Автозапись"
                        binding.signUpButton.setOnClickListener {
                            listener.onAutoSignClick(lessonData)
                        }
                    }
                    setMuted(false)
                } else {
                    binding.signUpButton.setOnClickListener(null)
                    binding.statusChip.visibility = View.VISIBLE
                    binding.statusChip.text = reason?.shortDescription

                    binding.signUpButton.visibility = View.GONE
                    setMuted(true)
                }
            }
        }

        private fun setMuted(isMuted: Boolean) {
            val alpha = if (isMuted) 0.6f else 1.0f
            binding.sportLessonCardView.alpha = alpha
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