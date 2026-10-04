package dev.alllexey.itmowidgets.core.ui

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkBinding
import dev.alllexey.itmowidgets.databinding.ViewLinkVotePillBinding
import java.net.URI
import java.util.Locale

/** The site without `www.`, or the raw text when it is not a URI. */
fun SubjectLink.host(): String =
    runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: url

fun SubjectLink.displayTitle(): String = title ?: host()

/** What a link row shows after its text: nothing, the own badge or the votes of another student's link. */
sealed interface LinkRowTrailing {
    data object None : LinkRowTrailing
    data object Own : LinkRowTrailing
    data class Votes(val link: SubjectLink, val canVote: Boolean, val onVote: (up: Boolean) -> Unit) : LinkRowTrailing
}

/**
 * One row of the links groups on the subject page and in the links sheet; own and others' links share
 * the style, an own link is told by the «моя» badge only. [icon] is the category symbol, null for none.
 */
fun ItemSubjectLinkBinding.bind(@DrawableRes icon: Int?, title: CharSequence, caption: CharSequence?, trailing: LinkRowTrailing) {
    this.icon.isVisible = icon != null
    icon?.let(this.icon::setImageResource)
    this.title.text = title
    meta.text = caption
    meta.isVisible = !caption.isNullOrEmpty()
    ownBadge.isVisible = trailing == LinkRowTrailing.Own
    votes.root.isVisible = trailing is LinkRowTrailing.Votes
    if (trailing is LinkRowTrailing.Votes) votes.bind(trailing.link, trailing.canVote, trailing.onVote)
    // Without a trailing view the text reaches the row's 16 dp content edge.
    val end = if (trailing == LinkRowTrailing.None) R.dimen.design_card_padding else R.dimen.design_spacing_related
    root.setPaddingRelative(root.paddingStart, root.paddingTop, root.resources.getDimensionPixelSize(end), root.paddingBottom)
    root.contentDescription = listOfNotNull(
        title,
        caption?.takeIf { it.isNotEmpty() },
        root.context.getString(R.string.links_mine).takeIf { trailing == LinkRowTrailing.Own },
    ).joinToString(", ")
}

/** Marks a link row's long press as the link's actions for TalkBack. */
fun ItemSubjectLinkBinding.describeActions() {
    ViewCompat.replaceAccessibilityAction(root, AccessibilityActionCompat.ACTION_LONG_CLICK,
        root.context.getString(R.string.links_actions), null)
}

/**
 * «▲ N ▼» of another student's link. The arrows exist while [canVote]; without them the pill keeps the
 * score alone. [onVote] gets `true` for the up arrow; tapping the current arrow takes the vote back upstream.
 */
fun ViewLinkVotePillBinding.bind(link: SubjectLink, canVote: Boolean, onVote: (up: Boolean) -> Unit) =
    bindVotes(link.score, link.myVote, canVote, root.context.getString(R.string.links_score, link.score), onVote = onVote)

/**
 * The vote pill for any rated item: arrows while [canVote] (disabled unless [enabled]), the score in the accent
 * once the viewer voted and in the error colour below zero. [upDescription] and [downDescription] name the arrows.
 */
fun ViewLinkVotePillBinding.bindVotes(
    score: Int,
    myVote: Int,
    canVote: Boolean,
    scoreDescription: String,
    enabled: Boolean = true,
    @StringRes upDescription: Int = R.string.links_vote_up,
    @StringRes downDescription: Int = R.string.links_vote_down,
    onVote: (up: Boolean) -> Unit,
) {
    val context = root.context
    voteUp.isVisible = canVote
    voteDown.isVisible = canVote
    voteUp.isEnabled = enabled
    voteDown.isEnabled = enabled
    voteUp.contentDescription = context.getString(upDescription)
    voteDown.contentDescription = context.getString(downDescription)
    this.score.text = if (score < 0) "−${-score}" else String.format(Locale.getDefault(), "%d", score)
    this.score.contentDescription = scoreDescription
    // The arrows pad their icons towards the number; alone, the number keeps the pill's 16 dp inset plus 12 dp.
    val resources = context.resources
    val margin = if (canVote) 0
        else resources.getDimensionPixelSize(R.dimen.design_spacing_group) +
            resources.getDimensionPixelSize(R.dimen.design_spacing_content)
    this.score.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = margin
        marginEnd = margin
    }
    val accent = context.color.primary
    val neutral = context.color.onSurfaceVariant
    this.score.setTextColor(when {
        myVote != 0 -> accent
        score < 0 -> context.color.resolve(androidx.appcompat.R.attr.colorError)
        else -> context.color.onSurface
    })
    voteUp.imageTintList = ColorStateList.valueOf(if (myVote > 0) accent else neutral)
    voteDown.imageTintList = ColorStateList.valueOf(if (myVote < 0) accent else neutral)
    voteUp.isSelected = myVote > 0
    voteDown.isSelected = myVote < 0
    voteUp.setOnClickListener { onVote(true) }
    voteDown.setOnClickListener { onVote(false) }
}
