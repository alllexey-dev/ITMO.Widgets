package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.ui.bindGroupPosition
import dev.alllexey.itmowidgets.core.ui.bindVotes
import dev.alllexey.itmowidgets.core.ui.shortPersonName
import dev.alllexey.itmowidgets.core.ui.userDisplayName
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemOwnTeacherReviewBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherReviewBinding

/**
 * Another viewer's or a copied review as a row of the others' group. The top line is the caption with ⋮ for
 * reporting; the footer names who wrote it (a named author or the source, both links, otherwise
 * «Анонимный отзыв»), then the verification and, at the end, the vote pill. Every mutable property is set here.
 */
internal fun ItemTeacherReviewBinding.bind(item: ProfileItem.Review, actions: ProfileActions) {
    val context = root.context
    val review = item.review
    root.bindGroupPosition(item.position)
    val meta = listOfNotNull(review.subject, review.written?.text(context)).joinToString(", ")
    this.meta.text = meta
    this.meta.isVisible = meta.isNotEmpty()
    text.text = review.text
    val canReport = item.canReport && review.origin.let { it is ReviewOrigin.Community && !it.reportedByMe }
    val community = review.origin as? ReviewOrigin.Community
    val copy = review.origin as? ReviewOrigin.Reviews
    val author = community?.author
    this.author.isVisible = author != null
    // «Фамилия И. О.» keeps the row short; TalkBack still reads the full name.
    this.author.text = author?.let { if (it.name.isBlank()) context.userDisplayName(it.name, it.isu) else shortPersonName(it.name) }
    this.author.contentDescription = author?.let { context.userDisplayName(it.name, it.isu) }
    this.author.setOnClickListener(author?.let { View.OnClickListener { actions.onAuthor(author.isu) } })
    anonymous.isVisible = community != null && author == null
    origin.isVisible = this.author.isVisible || anonymous.isVisible || copy != null
    source.isVisible = copy != null
    // The label is just the source; «Reviews» stays for TalkBack and for a copy without a source title.
    source.text = copy?.let { it.sourceTitle ?: context.getString(R.string.teacher_review_source_default) }
    source.contentDescription = copy?.sourceTitle?.let { context.getString(R.string.teacher_review_source, it) }
    source.setOnClickListener(copy?.let { View.OnClickListener { actions.onSource(copy.sourceUrl) } })
    verified.root.isVisible = community?.verified == true
    verified.root.setText(R.string.teacher_review_verified)
    unverified.isVisible = community != null && !community.verified
    verification.isVisible = verified.root.isVisible || unverified.isVisible
    // Without arrows a zero score says nothing and is left out.
    votes.root.isVisible = item.canVote || review.score != 0
    votes.bindVotes(review.score, review.myVote, item.canVote, context.getString(R.string.teacher_review_score, review.score),
        enabled = !item.busy, upDescription = R.string.teacher_review_vote_up, downDescription = R.string.teacher_review_vote_down,
    ) { up -> actions.onVote(review.id, up) }
    footer.isVisible = origin.isVisible || verification.isVisible || votes.root.isVisible
    // Without a report the place of ⋮ stays, so captions break at one width from row to row.
    more.visibility = if (canReport) View.VISIBLE else View.INVISIBLE
    top.isVisible = meta.isNotEmpty() || canReport
    root.bindReviewPadding(top = top.isVisible, footer = footer.isVisible)
    more.isEnabled = !item.busy
    more.setOnClickListener(if (canReport) View.OnClickListener { anchor ->
        (anchor as ImageButton).showMenu(R.string.teacher_review_report to { actions.onReport(review.id) })
    } else null)
}

/**
 * The viewer's own review, first in the group: `мой`, a status pill while it is not public, how others see it, a
 * rejection reason, the text and, once published, the verification mark and the read-only score. Editing and
 * deleting are in the menu.
 */
internal fun ItemOwnTeacherReviewBinding.bind(item: ProfileItem.OwnReview, actions: ProfileActions) {
    val context = root.context
    val review = item.review
    root.bindGroupPosition(item.position)
    bindStatus(context, review)
    val published = review.status == OwnReviewStatus.PUBLISHED
    verified.root.isVisible = published && review.verified
    verified.root.setText(R.string.teacher_review_verified_mine)
    unverified.isVisible = published && !review.verified
    meta.text = listOfNotNull(
        review.subject,
        context.getString(if (review.anonymous) R.string.teacher_review_anonymous_mine else R.string.teacher_review_named_mine),
    ).joinToString(", ")
    val note = review.reviewNote.takeIf { review.status == OwnReviewStatus.REJECTED }
    reason.isVisible = note != null
    reason.text = note?.let { context.getString(R.string.teacher_review_reason, it) }
    text.text = review.text
    verification.isVisible = published
    votes.root.isVisible = published
    votes.bindVotes(review.score, 0, canVote = false, context.getString(R.string.teacher_review_score, review.score)) { }
    footer.isVisible = published
    root.bindReviewPadding(top = true, footer = published)
    more.isEnabled = !item.busy
    more.setOnClickListener { anchor ->
        (anchor as ImageButton).showMenu(
            R.string.teacher_review_edit to actions.onEditReview,
            R.string.teacher_review_delete to actions.onDeleteReview,
        )
    }
}

/**
 * One bottom edge for every review row: a footer brings its own 48 dp row, so it needs only the row's 4 dp; a row
 * that ends with its text (or a rejection reason) gets the content padding instead. The same holds at the top.
 */
private fun View.bindReviewPadding(top: Boolean, footer: Boolean) {
    val related = resources.getDimensionPixelSize(R.dimen.design_spacing_related)
    val content = resources.getDimensionPixelSize(R.dimen.design_card_padding)
    updatePadding(top = if (top) related else content, bottom = if (footer) related else content)
}

private fun ItemOwnTeacherReviewBinding.bindStatus(context: Context, review: OwnTeacherReview) {
    val colors = context.color
    val (text, tone) = when (review.status) {
        OwnReviewStatus.PENDING -> R.string.teacher_review_status_pending to colors.tertiary
        OwnReviewStatus.REJECTED -> R.string.teacher_review_status_rejected to colors.error
        OwnReviewStatus.HIDDEN -> R.string.teacher_review_status_hidden to colors.error
        OwnReviewStatus.PUBLISHED -> null to colors.onSurface
    }
    status.isVisible = text != null
    status.text = text?.let(context::getString)
    // A light wash of the tone rather than its container: content-based palettes make containers as dark as the tone.
    status.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(tone, PILL_ALPHA))
    status.setTextColor(tone)
}

private fun ImageButton.showMenu(vararg entries: Pair<Int, () -> Unit>) {
    val popup = PopupMenu(context, this)
    entries.forEachIndexed { index, (title, _) -> popup.menu.add(0, index, index, title) }
    popup.setOnMenuItemClickListener { item ->
        entries.getOrNull(item.itemId)?.second?.invoke()
        true
    }
    popup.show()
}

/** 12 % of the tone, the same wash as `review_verified_container`. */
private const val PILL_ALPHA = 31
