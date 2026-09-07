package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ViewRecordbookScoreBinding
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.displayedScore
import kotlin.math.roundToInt

/** One accessible result, not a second interactive target inside the subject card. */
class RecordbookScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val binding = ViewRecordbookScoreBinding.inflate(LayoutInflater.from(context), this)
    // The base ring already fits enlarged type; grow only when its text needs more room.
    private val diameter = maxOf(72 * resources.displayMetrics.density,
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 56f, resources.displayMetrics)).roundToInt()

    init {
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        binding.scoreRing.indicatorSize = diameter
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(resolveSize(diameter, widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(resolveSize(diameter, heightMeasureSpec), MeasureSpec.EXACTLY)
        )
    }

    fun bind(subject: RecordbookSubject, sport: RecordbookSportState? = null) {
        val progress = subject.displayedScore(sport)
        val rate = subject.normalizedRate
        val compactGrade = subject.compactGradeCode
        val icon = when {
            rate == RecordbookRate.Credit -> R.drawable.ic_check_rounded
            subject.status == RecordbookSubjectStatus.ATTENTION && compactGrade == null -> R.drawable.ic_close_rounded
            else -> null
        }
        binding.rateIcon.isVisible = icon != null
        icon?.let(binding.rateIcon::setImageResource)
        binding.rate.isVisible = icon == null
        binding.rate.text = compactGrade ?: context.getString(R.string.recordbook_score_pending)
        binding.points.text = progress.value?.let(::formatRecordbookNumber)
            ?: context.getString(R.string.recordbook_score_pending)
        binding.points.isVisible = progress.value != null
        binding.scoreRing.setIndicatorColor(if (subject.isPhysicalEducation) {
            ContextCompat.getColor(context, R.color.sport_score_attendance)
        } else subject.status.progressColor(context))
        // Bind final geometry atomically; recycled cards must never animate another subject's score.
        binding.scoreRing.setProgressCompat(progress.progress, false)
        contentDescription = context.getString(R.string.recordbook_result_accessibility,
            subject.displayRate(context), progress.value?.let {
                context.getString(R.string.recordbook_points_out_of, formatRecordbookNumber(it), "100")
            } ?: context.getString(R.string.recordbook_points_missing))
        if (subject.isPhysicalEducation) {
            contentDescription = "${context.getString(R.string.recordbook_sport_source)}. $contentDescription"
        }
    }
}
