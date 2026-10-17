import Shared
import SwiftUI

/// What the review rows report, in `UserProfileViewModel`'s terms. `vote` names the arrow that was tapped (`up`);
/// tapping the arrow of the current vote takes it back upstream. `edit` and `delete` belong to the viewer's own review.
struct TeacherReviewActions {
    var vote: (String, Bool) -> Void = { _, _ in }
    var report: (UserProfileUiStateContent, String) -> Void = { _, _ in }
    var author: (Int32) -> Void = { _ in }
    var source: (String) -> Void = { _ in }
    var edit: (UserProfileUiStateContent) -> Void = { _ in }
    var delete: () -> Void = {}
}

/// The reviews of the teaching block: the heading with the count, the AI summary, the own review, then the others' reviews
/// as one group.
struct ProfileReviewsSection: View {
    let page: UserProfileUiStateContent
    let reviews: ProfileReviews
    let actions: UserProfileContentActions

    var body: some View {
        if reviews.count > 0 || reviews.summary != nil {
            ItmoSectionHeader(
                title: AppStrings.string("teacher_reviews_title"),
                count: reviews.count > 0 ? "\(reviews.count)" : nil
            )
            .accessibilityIdentifier("user_profile_reviews")
            if let summary = reviews.summary {
                TeacherSummaryCardView(summary: summary, expanded: reviews.summaryExpanded, toggle: actions.toggleSummary)
            }
            if let mine = reviews.mine {
                ItmoListGroup {
                    OwnTeacherReviewRowView(
                        review: mine,
                        busy: reviews.busyId == mine.id,
                        edit: { actions.review.edit(page) },
                        delete: actions.review.delete
                    )
                }
            }
            if !reviews.items.isEmpty {
                ItmoListGroup(inset: ItmoKit.padding) {
                    ForEach(reviews.items, id: \.id) { review in
                        TeacherReviewRowView(
                            review: review,
                            canVote: reviews.canVote,
                            canReport: reviews.canReport,
                            busy: reviews.busyId == review.id,
                            actions: actions.review,
                            report: { actions.review.report(page, review.id) }
                        )
                    }
                }
            }
        }
    }
}

/// Another viewer's or a copied review: the caption (subject, date) with the more button for reporting, the full text,
/// then who wrote it (a named author or the source, both links, otherwise `teacher_review_anonymous`) with the verification
/// under it and the votes beside them. Without `canVote` the arrows go and a zero score is left out. The more button
/// offers `teacher_review_report` only on a community review the viewer has not reported yet, else keeps its place invisibly.
/// While `busy` (a vote in flight) the arrows and the more button ignore taps.
struct TeacherReviewRowView: View {
    let review: TeacherReview
    let canVote: Bool
    let canReport: Bool
    let busy: Bool
    let actions: TeacherReviewActions
    let report: () -> Void

    var body: some View {
        let community = review.origin as? ReviewOriginCommunity
        let copy = review.origin as? ReviewOriginReviews
        let reportable = canReport && community != nil && community?.reportedByMe == false
        let meta = [review.subject, review.written.map(Self.text)].compactMap { $0 }.joined(separator: ", ")
        let showsVotes = canVote || review.score != 0
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            if !meta.isEmpty || reportable {
                HStack(alignment: .center, spacing: ItmoSpacing.compact) {
                    Text(verbatim: meta)
                        .font(.itmo(.bodySmall))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    ReviewMoreMenu(enabled: !busy, visible: reportable) {
                        Button(action: report) {
                            Label { Text(verbatim: AppStrings.string("teacher_review_report")) } icon: { AppSymbol.flag.image }
                        }
                    }
                }
            }
            Text(verbatim: review.text)
                .font(.itmo(.bodyMedium))
                .fixedSize(horizontal: false, vertical: true)
            if community != nil || copy != nil || showsVotes {
                HStack(alignment: .center, spacing: ItmoSpacing.compact) {
                    VStack(alignment: .leading, spacing: 2) {
                        origin(community: community, copy: copy)
                        if let community {
                            ReviewVerification(verified: community.verified, key: "teacher_review_verified")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if showsVotes {
                        ItmoVotePill(
                            score: Int(review.score),
                            myVote: Int(review.myVote),
                            upLabel: AppStrings.string("teacher_review_vote_up"),
                            downLabel: AppStrings.string("teacher_review_vote_down"),
                            enabled: canVote,
                            onUp: { actions.vote(review.id, true) },
                            onDown: { actions.vote(review.id, false) }
                        )
                        .disabled(busy)
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel(Text(verbatim: AppStrings.string("teacher_review_score", [review.score])))
                    }
                }
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 12)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("teacher_review")
    }

    /// When it was written: the month and year, or `teacher_review_before_year`.
    private static func text(_ date: any ReviewDate) -> String {
        if let month = date as? ReviewDateMonth { return month.text() }
        if let before = date as? ReviewDateBeforeYear { return AppStrings.string("teacher_review_before_year", [before.year]) }
        return ""
    }

    /// A named author in the accent, the source of a copy with an arrow out, or `teacher_review_anonymous`.
    @ViewBuilder
    private func origin(community: ReviewOriginCommunity?, copy: ReviewOriginReviews?) -> some View {
        if let author = community?.author {
            Button { actions.author(author.isu) } label: {
                Text(verbatim: author.name.isEmpty ? author.displayName().resolved : UserNamesKt.shortPersonName(name: author.name))
                    .font(.itmo(.bodyMedium))
            }
            .buttonStyle(.borderless)
            .itmoTint()
            .accessibilityLabel(Text(verbatim: author.displayName().resolved))
            .accessibilityIdentifier("teacher_review_author")
        } else if let copy {
            Button { actions.source(copy.sourceUrl) } label: {
                HStack(spacing: 4) {
                    Text(verbatim: copy.sourceTitle ?? AppStrings.string("teacher_review_source_default"))
                    AppSymbol.openInNew.image.font(.itmo(.bodySmall)).accessibilityHidden(true)
                }
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(Text(verbatim: copy.sourceTitle.map { AppStrings.string("teacher_review_source", [$0]) }
                ?? AppStrings.string("teacher_review_source_default")))
            .accessibilityIdentifier("teacher_review_source")
        } else if community != nil {
            Text(verbatim: AppStrings.string("teacher_review_anonymous"))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
        }
    }
}

/// The viewer's own review, a group of its own above the others: the mine badge, a status pill while it is not public
/// (pending in tertiary, rejected or hidden in error), the more button with edit and delete, the subject
/// and how others see it, a rejection reason, the full text and, once published, the verification and the read-only
/// score. While `busy` (a deletion in flight) the more button ignores taps.
struct OwnTeacherReviewRowView: View {
    let review: OwnTeacherReview
    let busy: Bool
    let edit: () -> Void
    let delete: () -> Void

    var body: some View {
        let published = review.status == .published
        let visibility = AppStrings.string(review.anonymous ? "teacher_review_anonymous_mine" : "teacher_review_named_mine")
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            HStack(spacing: ItmoSpacing.compact) {
                ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("teacher_review_mine_badge")))
                if let status = review.status.pill { ItmoStatusPill(pill: status) }
                Spacer(minLength: 0)
                ReviewMoreMenu(enabled: !busy, visible: true) {
                    Button(action: edit) {
                        Label { Text(verbatim: AppStrings.string("teacher_review_edit")) } icon: { AppSymbol.edit.image }
                    }
                    Button(role: .destructive, action: delete) {
                        Label { Text(verbatim: AppStrings.string("teacher_review_delete")) } icon: { AppSymbol.delete.image }
                    }
                }
            }
            Text(verbatim: [review.subject, visibility].compactMap { $0 }.joined(separator: ", "))
                .font(.itmo(.bodySmall))
                .foregroundStyle(.secondary)
            if review.status == .rejected, let note = review.reviewNote {
                Text(verbatim: AppStrings.string("teacher_review_reason", [note]))
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(ItmoColor.error)
            }
            Text(verbatim: review.text)
                .font(.itmo(.bodyMedium))
                .fixedSize(horizontal: false, vertical: true)
            if published {
                HStack(spacing: ItmoSpacing.compact) {
                    ReviewVerification(verified: review.verified, key: "teacher_review_verified_mine")
                        .frame(maxWidth: .infinity, alignment: .leading)
                    ItmoVotePill(
                        score: Int(review.score),
                        myVote: 0,
                        upLabel: AppStrings.string("teacher_review_vote_up"),
                        downLabel: AppStrings.string("teacher_review_vote_down"),
                        enabled: false,
                        onUp: {},
                        onDown: {}
                    )
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("teacher_review_score", [review.score])))
                }
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 12)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("teacher_review_own")
    }
}

extension OwnReviewStatus {
    /// The status pill's words and tone; a published review shows none.
    var pill: ItmoPill? {
        switch self {
        case .pending: ItmoPill(text: AppStrings.string("teacher_review_status_pending"), tone: .tertiary)
        case .rejected: ItmoPill(text: AppStrings.string("teacher_review_status_rejected"), tone: .bad)
        case .hidden: ItmoPill(text: AppStrings.string("teacher_review_status_hidden"), tone: .bad)
        case .published: nil
        }
    }
}

/// The verification with a check in the accent, else the quiet `teacher_review_unverified`.
private struct ReviewVerification: View {
    let verified: Bool
    let key: String

    var body: some View {
        if verified {
            Label { Text(verbatim: AppStrings.string(key)) } icon: { AppSymbol.check.image }
                .font(.itmo(.bodySmall))
                .foregroundStyle(ItmoColor.primary)
        } else {
            Text(verbatim: AppStrings.string("teacher_review_unverified"))
                .font(.itmo(.bodySmall))
                .foregroundStyle(.secondary)
        }
    }
}

/// The more button of a review with its menu, a 44 pt target. A row without actions keeps it in place, invisible
/// and silent, so captions break at one width from row to row.
private struct ReviewMoreMenu<Items: View>: View {
    let enabled: Bool
    let visible: Bool
    @ViewBuilder let items: Items

    var body: some View {
        Menu { items } label: {
            AppSymbol.moreVert.image
                .foregroundStyle(.secondary)
                .frame(width: ItmoMetrics.touchTarget, height: ItmoMetrics.touchTarget)
                .contentShape(.rect)
        }
        .tint(.secondary)
        // A 44 pt target that takes no more height in the caption line than its text.
        .padding(.vertical, -10)
        .disabled(!enabled || !visible)
        .opacity(visible ? 1 : 0)
        .accessibilityHidden(!visible)
        .accessibilityLabel(Text(verbatim: AppStrings.string("teacher_review_actions")))
        .accessibilityIdentifier("teacher_review_more")
        .padding(.trailing, -ItmoSpacing.compact)
    }
}

/// Backend's AI summary of a teacher's reviews, first in the section: the header with the review count and the AI mark, the
/// tone (only when `showsLevel`, never at low confidence), the description, pros and cons, tags, and the five scales
/// folded behind a text button. Empty blocks are hidden.
struct TeacherSummaryCardView: View {
    let summary: TeacherSummary
    let expanded: Bool
    let toggle: () -> Void

    var body: some View {
        ItmoCard {
            header
            if summary.showsLevel { level }
            Text(verbatim: summary.description_)
                .font(.itmo(.bodyMedium))
                .fixedSize(horizontal: false, vertical: true)
            points(summary.pros, symbol: .add, title: "teacher_summary_pros")
            points(summary.cons, symbol: .remove, title: "teacher_summary_cons")
            if !summary.tags.isEmpty {
                ItmoFlow(spacing: 6) {
                    ForEach(summary.tags, id: \.self) { tag in
                        Text(verbatim: AppStrings.string("teacher_summary_tag_\(tag.name.lowercased())"))
                            .font(.itmo(.labelMedium))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 28)
                            .overlay(RoundedRectangle(cornerRadius: ItmoCorner.small).strokeBorder(ItmoColor.outlineVariant))
                    }
                }
                .padding(.top, 4)
            }
            if expanded { scales }
            Button(action: toggle) {
                HStack(spacing: 4) {
                    Text(verbatim: AppStrings.string(expanded ? "teacher_summary_less" : "teacher_summary_more"))
                    (expanded ? AppSymbol.summaryCollapse : AppSymbol.summaryExpand).image.accessibilityHidden(true)
                }
                .font(.itmo(.labelLarge, emphasized: true))
                .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.borderless)
            .itmoTint()
            .accessibilityValue(Text(verbatim: AppStrings.string(expanded ? "teacher_summary_expanded" : "teacher_summary_collapsed")))
            .accessibilityIdentifier("teacher_summary_toggle")
            .padding(.bottom, -ItmoSpacing.compact)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("teacher_summary")
    }

    private var header: some View {
        let title = AppStrings.plural("teacher_summary_label", count: Int(summary.reviewCount))
        return HStack(spacing: ItmoSpacing.compact) {
            AppSymbol.autoAwesome.image.foregroundStyle(ItmoColor.primary)
            Text(verbatim: title).font(.itmo(.titleSmall, emphasized: true))
            Spacer(minLength: 0)
            Text(verbatim: AppStrings.string("teacher_summary_ai")).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(title), \(AppStrings.string("teacher_summary_ai_description"))"))
    }

    /// The dot and the words of the tone, read as `teacher_level_description`.
    private var level: some View {
        let tone = summary.level.tone()
        let label = tone.label.resolved
        return HStack(spacing: ItmoSpacing.compact) {
            Circle().fill(summary.level.color).frame(width: 10, height: 10)
            Text(verbatim: label).font(.itmo(.labelLarge, emphasized: true))
        }
        .padding(.top, 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: tone.description(resolvedLabel: label).resolved))
    }

    /// One line per point with a small plus or minus; the block reads as `teacher_summary_list`.
    @ViewBuilder
    private func points(_ points: [String], symbol: AppSymbol, title: String) -> some View {
        if !points.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(points.enumerated()), id: \.offset) { _, point in
                    Label { Text(verbatim: point).fixedSize(horizontal: false, vertical: true) } icon: {
                        symbol.image.font(.itmo(.bodySmall, emphasized: true)).foregroundStyle(.secondary)
                    }
                    .font(.itmo(.bodyMedium))
                }
            }
            .padding(.top, 4)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(
                Text(verbatim: AppStrings.string("teacher_summary_list", [AppStrings.string(title), points.joined(separator: "; ")]))
            )
        }
    }

    /// The five scales in their order; a missing one reads `teacher_summary_value_not_enough_data`.
    private var scales: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            ForEach(SummaryScaleKind.allCases, id: \.self) { kind in
                let scale = summary.scales.first { $0.kind == kind }
                let value = scale?.value ?? .notEnoughData
                let known = value != .notEnoughData
                let name = AppStrings.string("teacher_summary_scale_\(kind.name.lowercased())")
                let valueText = AppStrings.string(value.key(kind))
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(verbatim: name).font(.itmo(.bodyMedium))
                        Spacer(minLength: ItmoSpacing.compact)
                        Text(verbatim: valueText)
                            .font(.itmo(.labelLarge, emphasized: true))
                            .foregroundStyle(known ? Color.primary : Color.secondary)
                    }
                    if known, let reason = scale?.reason {
                        Text(verbatim: reason).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.top, 4)
        .accessibilityIdentifier("teacher_summary_scales")
    }
}

extension SummaryScaleValue {
    /// The explains and attitude scales have their own words; the others share low, medium, high.
    func key(_ kind: SummaryScaleKind) -> String {
        guard self != .notEnoughData else { return "teacher_summary_value_not_enough_data" }
        let level = name.lowercased()
        switch kind {
        case .explains, .attitude: return "teacher_summary_value_\(kind.name.lowercased())_\(level)"
        default: return "teacher_summary_value_\(level)"
        }
    }
}

extension TeacherLevel {
    /// The `teacher_level_*` token of the tone.
    var color: Color {
        switch self {
        case .veryNegative: ItmoColor.teacherLevelVeryNegative
        case .negative: ItmoColor.teacherLevelNegative
        case .mixed: ItmoColor.teacherLevelMixed
        case .positive: ItmoColor.teacherLevelPositive
        case .veryPositive: ItmoColor.teacherLevelVeryPositive
        }
    }
}
