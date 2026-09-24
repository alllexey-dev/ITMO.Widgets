package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ViewLinkVotesBinding
import java.util.Locale

/**
 * Others' links vote with arrows while [canVote]; an own link shows its score, or a lock while it is private.
 * [onVote] gets `true` for the up arrow; the view model turns a tap on the current arrow into taking the vote back.
 */
internal fun ViewLinkVotesBinding.bind(link: SubjectLink, canVote: Boolean, onVote: (up: Boolean) -> Unit) {
    val context = root.context
    val arrows = !link.isMine && canVote
    val showScore = !link.isMine || link.visibility != LinkVisibility.PRIVATE
    voteUp.isVisible = arrows
    voteDown.isVisible = arrows
    score.isVisible = showScore
    privateIcon.isVisible = !showScore
    score.text = String.format(Locale.getDefault(), "%d", link.score)
    score.contentDescription = context.getString(R.string.links_score, link.score)
    val overlap = if (arrows) -context.resources.getDimensionPixelSize(R.dimen.design_spacing_content) else 0
    score.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = overlap; bottomMargin = overlap }
    val accent = context.color.primary
    val neutral = context.color.onSurfaceVariant
    score.setTextColor(if (link.myVote != 0) accent else context.color.onSurface)
    voteUp.imageTintList = ColorStateList.valueOf(if (link.myVote > 0) accent else neutral)
    voteDown.imageTintList = ColorStateList.valueOf(if (link.myVote < 0) accent else neutral)
    voteUp.isSelected = link.myVote > 0
    voteDown.isSelected = link.myVote < 0
    voteUp.setOnClickListener { onVote(true) }
    voteDown.setOnClickListener { onVote(false) }
}
