import Shared
import SwiftUI

// The subject page's three tabs over their parts of `RecordbookSubjectUiStateContent`.

/// Scores: a BARS notice, the hero with the grade scale and the connected sheet, the sport points of physical
/// education, the checkpoints by group with their minimums, or the quiet offers without them.
struct SubjectScoresTab: View {
    let scores: SubjectScores
    let actions: RecordbookSubjectContentActions

    var body: some View {
        if let notice = scores.barsNotice {
            RecordbookBarsNoticeRow(notice: notice, signIn: actions.barsSignIn, retry: actions.retry)
        }
        SubjectHeroCard(hero: scores.hero, sheet: scores.sheet, actions: actions)
        if let sport = scores.sport {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("subject_sport_title"), symbol: .sprint, tone: .ok,
                    trailing: sport.resolved, chevron: true, action: actions.openSport
                )
            }
        }
        ForEach(scores.groups.indices, id: \.self) { index in
            let group = scores.groups[index]
            if let title = group.title {
                HStack(alignment: .firstTextBaseline) {
                    Text(verbatim: title.resolved)
                        .font(.itmo(.titleMedium, emphasized: true))
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: ItmoSpacing.compact)
                    if let sum = group.sum {
                        Text(verbatim: sum.resolved)
                            .font(.itmo(.bodyMedium))
                            .monospacedDigit()
                            .foregroundStyle(group.below ? ItmoColor.error : .secondary)
                    }
                }
                .padding(.top, ItmoSpacing.compact)
                .padding(.horizontal, ItmoSpacing.related)
            }
            ItmoListGroup(inset: ItmoKit.padding) {
                ForEach(group.rows, id: \.id) { SubjectControlRowView(row: $0) }
            }
        }
        if let error = scores.controlsError {
            ItmoListGroup {
                ItmoRow(title: AppStrings.string("common_load_error_title"), subtitle: AppErrorTextsKt.toUiText(error).resolved)
                    .accessory {
                        Button(AppStrings.string("common_retry"), action: actions.retry).buttonStyle(.borderless).itmoTint()
                    }
            }
        }
        if scores.barsOffer || !scores.sheetOffer.isEmpty {
            ItmoListGroup {
                if scores.barsOffer { RecordbookBarsOfferRow(connect: actions.barsSignIn) }
                if !scores.sheetOffer.isEmpty { SheetOfferRow(options: scores.sheetOffer, open: actions.sheetScores) }
            }
        }
        if scores.noDetails {
            ItmoGroupNote(text: AppStrings.string("recordbook_details_empty_description"))
        }
    }
}

/// "63.5 of 100", the grade pill over its source, the scale, the next step; the connected sheet with its menu.
private struct SubjectHeroCard: View {
    let hero: SubjectHero
    let sheet: SubjectSheetLine?
    let actions: RecordbookSubjectContentActions

    var body: some View {
        ItmoCard(tone: .primary, hero: true) {
            HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.compact) {
                Text(verbatim: hero.points ?? AppStrings.string("recordbook_score_pending"))
                    .font(.itmo(.displayMedium, emphasized: true))
                    .monospacedDigit()
                Text(verbatim: AppStrings.string("subject_score_out_of")).foregroundStyle(.secondary)
                Spacer(minLength: ItmoSpacing.compact)
                VStack(alignment: .trailing, spacing: ItmoSpacing.related) {
                    if let grade = hero.grade {
                        ItmoStatusPill(pill: ItmoPill(text: grade.resolved, tone: hero.gradeTone.kit))
                    }
                    Text(verbatim: hero.source.resolved).font(.itmo(.labelSmall)).foregroundStyle(.secondary)
                }
            }
            ItmoGradeScale(
                share: hero.points == nil ? 0 : Double(hero.share),
                ticks: hero.ticks.map { ($0.label.resolved, $0.score) }
            )
            .padding(.top, ItmoSpacing.related)
            if let next = hero.next {
                Text(verbatim: next.resolved).font(.itmo(.titleSmall, emphasized: true)).foregroundStyle(ItmoColor.primary)
            }
            if let sheet {
                Divider().padding(.vertical, ItmoSpacing.related)
                HStack(spacing: ItmoSpacing.content) {
                    AppSymbol.table.image.foregroundStyle(ItmoColor.primary)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(verbatim: sheet.title.resolved).font(.itmo(.bodyMedium, emphasized: true))
                        Text(verbatim: sheet.caption.resolved).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                        if let problem = sheet.problem {
                            Text(verbatim: problem.resolved).font(.itmo(.bodySmall)).foregroundStyle(ItmoColor.warning)
                        }
                    }
                    Spacer(minLength: 0)
                    Menu {
                        Button(AppStrings.string("sheet_scores_open")) { actions.openLink(sheet.url) }
                        Button(AppStrings.string("sheet_scores_change_total")) { actions.sheetScores(sheet.changeTotal) }
                        Button(AppStrings.string("sheet_scores_disconnect"), role: .destructive, action: actions.disconnectSheet)
                    } label: {
                        AppSymbol.moreVert.image.frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("sheet_scores_actions")))
                }
            }
        }
        .accessibilityIdentifier("subject.hero")
    }
}

/// The own scores from a sheet: one link opens its sheet at once, several ask which.
private struct SheetOfferRow: View {
    let options: [SubjectSheetOption]
    let open: (SheetScoresArgs) -> Void
    @State private var choosing = false

    var body: some View {
        ItmoRow(
            title: AppStrings.string("sheet_scores_hint"),
            subtitle: AppStrings.string("subject_sheet_offer_note"),
            symbol: .table, tone: .neutral, chevron: true
        ) {
            if options.count == 1 { open(options[0].args) } else { choosing = true }
        }
        .confirmationDialog(Text(verbatim: AppStrings.string("sheet_scores_choose_link")), isPresented: $choosing, titleVisibility: .visible) {
            ForEach(options.indices, id: \.self) { index in
                Button(options[index].title.resolved) { open(options[index].args) }
            }
        }
    }
}

/// A checkpoint: name, absent, "9 / 10"; the bar once graded; the minimum on the left and the date on the right.
private struct SubjectControlRowView: View {
    let row: SubjectControlRow

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: ItmoSpacing.compact) {
                Text(verbatim: row.name).font(.itmo(.bodyLarge)).lineLimit(2)
                Spacer(minLength: ItmoSpacing.related)
                if row.absent {
                    ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("subject_control_absent"), tone: .bad))
                }
                Text(verbatim: row.score.resolved)
                    .font(.itmo(.bodyLarge, emphasized: true))
                    .monospacedDigit()
                    .foregroundStyle(row.graded ? Color.primary : .secondary)
            }
            if let share = row.share {
                ProgressView(value: Double(share.floatValue)).tint(row.tone.kit.color)
            }
            if row.minimum != nil || row.date != nil {
                HStack {
                    Text(verbatim: row.minimum?.resolved ?? "")
                        .foregroundStyle(row.below ? ItmoColor.error : .secondary)
                    Spacer()
                    if let date = row.date { Text(verbatim: date.resolved).foregroundStyle(.secondary) }
                }
                .font(.itmo(.bodySmall))
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .accessibilityElement(children: .combine)
    }
}

/// Lessons: a schedule question, the teachers as rows with the tone of their reviews in words, the reviews' gate or
/// offer, then the lessons ahead.
struct SubjectLessonsTab: View {
    let lessons: SubjectLessons
    let actions: RecordbookSubjectContentActions

    var body: some View {
        if let binding = lessons.binding { SubjectBindingCard(binding: binding, actions: actions) }
        if !lessons.teachers.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("subject_teachers_title"))
            ItmoListGroup(inset: 68) {
                ForEach(lessons.teachers.indices, id: \.self) { index in
                    let teacher = lessons.teachers[index]
                    ItmoRow(
                        title: teacher.name,
                        subtitle: teacher.detail?.resolved,
                        chevron: teacher.isu != nil,
                        action: teacher.isu.map { isu in { actions.openTeacher(isu.int32Value) } }
                    ) {
                        ItmoAvatar(initials: teacher.initials)
                    }
                    .accessibilityIdentifier("subject.teacher.\(index)")
                }
            }
        }
        switch onEnum(of: lessons.reviews) {
        case .shown:
            EmptyView()
        case let .few(few):
            ItmoActionCard(
                title: AppStrings.string("subject_reviews_few_title"),
                reason: AppStrings.string("subject_reviews_few_text"),
                action: (AppStrings.string("subject_reviews_write"), { actions.writeReview(few.teacher) }),
                symbol: .editNote
            )
        case .off:
            ItmoGateCard(
                title: AppStrings.string("subject_reviews_off_title"),
                reason: AppStrings.string("subject_reviews_off_text"),
                fix: (AppStrings.string("subject_connect"), actions.connect),
                symbol: .cloudOff
            )
            .accessibilityIdentifier("subject.reviewsGate")
        }
        ItmoSectionHeader(title: AppStrings.string("subject_lessons_title"))
        switch onEnum(of: lessons.list) {
        case .none:
            ItmoGroupNote(text: AppStrings.string("subject_lessons_none"))
        case .loading:
            ItmoLoadingView()
        case let .failed(failed):
            ItmoListGroup {
                ItmoRow(title: AppStrings.string("common_load_error_title"), subtitle: AppErrorTextsKt.toUiText(failed.error).resolved)
                    .accessory {
                        Button(AppStrings.string("common_retry"), action: actions.retryLessons).buttonStyle(.borderless).itmoTint()
                    }
            }
        case .unmatched:
            ItmoGroupNote(text: AppStrings.string("subject_lessons_unmatched"))
        case let .content(content):
            ForEach(content.items, id: \.pairId) { item in
                SubjectLessonCardView(item: item, open: actions.openLesson)
            }
            if content.more > 0 {
                ItmoListGroup {
                    ItmoRow(
                        title: AppStrings.string("subject_lessons_all", [Int(content.more)]),
                        chevron: true, action: actions.showAllLessons
                    )
                }
            }
        }
    }
}

/// A lesson ahead under its day: the schedule's lesson card; a tap opens the lesson sheet.
private struct SubjectLessonCardView: View {
    let item: SubjectLessonItem
    let open: (LessonTarget) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            Text(verbatim: item.day.resolved)
                .font(.itmo(.labelLarge, emphasized: true))
                .foregroundStyle(.secondary)
                .padding(.horizontal, ItmoSpacing.related)
            Button { open(item.lesson.target) } label: {
                ItmoLessonCard(lesson: item.lesson.kitLesson(id: "\(item.pairId)")).contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
    }
}

/// Whether the schedule's subject is this one: yes or no for one candidate, a choice of several.
private struct SubjectBindingCard: View {
    let binding: SubjectBinding
    let actions: RecordbookSubjectContentActions

    var body: some View {
        ItmoCard {
            if binding.candidates.count == 1, let single = binding.candidates.first {
                Text(verbatim: AppStrings.string("subject_binding_proposal", [single.name])).font(.itmo(.bodyLarge))
                HStack {
                    ItmoProminentButton(title: AppStrings.string("subject_binding_confirm")) {
                        actions.confirmBinding(single.subjectId)
                    }
                    Button(AppStrings.string("subject_binding_reject"), action: actions.rejectBinding).buttonStyle(.glass)
                }
                .itmoTint()
                .padding(.top, ItmoSpacing.related)
            } else {
                Text(verbatim: AppStrings.string("subject_binding_ambiguous")).font(.itmo(.bodyLarge))
                ForEach(binding.candidates, id: \.subjectId) { candidate in
                    Button(candidate.name) { actions.confirmBinding(candidate.subjectId) }.buttonStyle(.borderless)
                }
                Button(AppStrings.string("subject_binding_reject"), action: actions.rejectBinding).buttonStyle(.borderless)
            }
        }
    }
}

/// Materials: chats, links by category with their votes, own links with their review state; only a gate without
/// the connection.
struct SubjectMaterialsTab: View {
    let materials: SubjectMaterials
    let actions: RecordbookSubjectContentActions

    var body: some View {
        switch onEnum(of: materials) {
        case .gate:
            ItmoGateCard(
                title: AppStrings.string("subject_materials_off_title"),
                reason: AppStrings.string("subject_materials_off_text"),
                fix: (AppStrings.string("subject_connect"), actions.connect),
                symbol: .cloudOff
            )
            .accessibilityIdentifier("subject.materialsGate")
        case .loading:
            ItmoLoadingView()
        case let .failed(failed):
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(failed.error).resolved,
                retry: actions.retry
            )
        case let .empty(empty):
            ItmoEmptyView(
                symbol: .link,
                title: AppStrings.string("subject_materials_empty_title"),
                description: AppStrings.string("subject_materials_empty_text"),
                action: (AppStrings.string("subject_link_add"), { actions.addLink(empty.args) })
            )
        case let .content(content):
            if !content.chats.isEmpty {
                ItmoSectionHeader(title: AppStrings.string("subject_materials_chats"))
                ItmoListGroup {
                    ForEach(content.chats, id: \.id) { SubjectLinkRowView(row: $0, links: content.args, chat: true, actions: actions) }
                }
            }
            ForEach(content.sections, id: \.self) { section in
                ItmoSectionHeader(title: section.title.resolved)
                ItmoListGroup {
                    ForEach(section.rows, id: \.id) { SubjectLinkRowView(row: $0, links: content.args, chat: false, actions: actions) }
                }
            }
            if content.previous > 0 {
                ItmoListGroup {
                    ItmoRow(
                        title: AppStrings.string("links_all_count", [Int(content.previous)]),
                        chevron: true, action: { actions.allLinks(content.args) }
                    )
                }
            }
        }
    }
}

/// A link: a tap opens it, a long press its actions; the votes on the right.
private struct SubjectLinkRowView: View {
    let row: SubjectLinkRow
    let links: SubjectLinksArgs
    let chat: Bool
    let actions: RecordbookSubjectContentActions

    var body: some View {
        HStack(spacing: ItmoSpacing.content) {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: row.icon.symbol, tone: chat ? .primary : .neutral, size: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: row.title).font(.itmo(.bodyLarge)).foregroundStyle(Color.primary).lineLimit(1)
                    Text(verbatim: row.line.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary).lineLimit(1)
                    if row.own {
                        HStack(spacing: 6) {
                            ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("subject_link_mine")))
                            if let status = row.status {
                                ItmoStatusPill(pill: ItmoPill(text: status.resolved, tone: row.statusTone.kit))
                            }
                        }
                        .padding(.top, 2)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(.rect)
            // A long press opens the actions without also opening the link.
            .onTapGesture { actions.openLink(row.url) }
            .onLongPressGesture { actions.linkActions(links, row.id) }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { actions.openLink(row.url) }
            .accessibilityAction(named: Text(verbatim: AppStrings.string("subject_link_more"))) {
                actions.linkActions(links, row.id)
            }
            if let score = row.score {
                ItmoVotePill(
                    score: Int(score.int32Value),
                    myVote: Int(row.myVote),
                    upLabel: AppStrings.string("links_vote_up"),
                    downLabel: AppStrings.string("links_vote_down"),
                    enabled: row.canVote,
                    onUp: { actions.vote(row.id, true) },
                    onDown: { actions.vote(row.id, false) }
                )
            }
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
        .accessibilityIdentifier("subject.link.\(row.id)")
    }
}
