package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.CircularProgressBar
import dev.alllexey.itmowidgets.databinding.ItemRecordbookSportBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSportProgress

internal class RecordbookSportHolder(
    private val binding: ItemRecordbookSportBinding,
    private val onRetry: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(subject: RecordbookSubject, sport: RecordbookSportState?) {
        val context = binding.root.context
        val content = sport as? RecordbookSportState.Content
        binding.scoreContent.isVisible = content != null
        binding.errorContent.isVisible = content == null
        binding.period.text = content?.periodLabel
        binding.period.visibility = if (content != null) View.VISIBLE else View.INVISIBLE
        binding.bonusNote.isVisible = content != null && content.score.bonus > content.score.creditedBonus
        if (content != null) {
            val score = content.score
            val progress = RecordbookSportProgress(score)
            binding.total.text = formatRecordbookNumber(score.total.toDouble())
            binding.attendance.text = formatRecordbookNumber(score.attendances.toDouble())
            binding.bonus.text = formatRecordbookNumber(score.creditedBonus.toDouble())
            binding.bonusNote.text = context.getString(R.string.recordbook_sport_bonus_limit, score.creditedBonus, score.bonus)
            binding.scoreResult.contentDescription = context.getString(R.string.recordbook_points_out_of, score.total.toString(), "100")
            binding.sportRing.setSectors(listOf(
                CircularProgressBar.Sector(ContextCompat.getColor(context, R.color.sport_score_attendance), progress.attendancePercentage),
                CircularProgressBar.Sector(ContextCompat.getColor(context, R.color.sport_score_bonus), progress.bonusPercentage)
            ))
        } else {
            binding.sportRing.setSectors(emptyList())
            binding.errorDescription.setText(if (sport == RecordbookSportState.Error)
                R.string.recordbook_sport_retry_description else R.string.recordbook_sport_no_period)
        }
        binding.retry.isVisible = sport == RecordbookSportState.Error
        binding.retry.setOnClickListener { onRetry() }
        binding.officialResult.text = if (subject.normalizedRate == RecordbookRate.InProgress)
            context.getString(R.string.recordbook_official_pending) else subject.displayRate(context)
        val icon = when (subject.status) {
            RecordbookSubjectStatus.PASSED -> R.drawable.ic_check_rounded
            RecordbookSubjectStatus.ATTENTION -> R.drawable.ic_close_rounded
            RecordbookSubjectStatus.IN_PROGRESS -> null
        }
        binding.officialIcon.isVisible = icon != null
        icon?.let(binding.officialIcon::setImageResource)
    }
}
