import Shared
import SwiftUI

/// The lesson sheet of one opening: the action is sent once (`act` gets it), the share goes through the share sheet
/// and the map to Apple Maps.
struct SportLessonSheet: View {
    let item: SportLessonItem
    /// The sheet as it is now; `true` once its action was sent.
    let present: (Bool) -> SportLessonSheetUi
    let act: (SportBookingAction) -> Void
    let openProfile: (Int32) -> Void
    /// "Make it a section" of a booked lesson as it is now; nil for any other lesson.
    var section: () -> SportSectionToggleUi? = { nil }
    var toggleSection: (Int64) -> Void = { _ in }

    @State private var submitted = false

    var body: some View {
        SportLessonSheetContent(
            sheet: present(submitted),
            act: { action in
                guard !submitted else { return }
                submitted = true
                act(action)
            },
            openProfile: openProfile,
            share: share,
            section: section(),
            toggleSection: toggleSection
        )
    }

    private func share(_ target: SportShareTarget) {
        guard let shares = IosKoin.shared.get(type: SportShares.self) as? SportShares else { return }
        let item = item.details
        Task { _ = try? await shares.lesson(item: item, target: target) }
    }
}

/// The sport lesson sheet (DECISIONS.md, Sport): what and when, the places large and as a bar, the status, the
/// clash with a lesson as an orange line that does not block, one main action with its note, the conditions, then the
/// teacher, the place, the comment, the friends and the share.
struct SportLessonSheetContent: View {
    let sheet: SportLessonSheetUi
    var act: (SportBookingAction) -> Void = { _ in }
    var openProfile: (Int32) -> Void = { _ in }
    var share: (SportShareTarget) -> Void = { _ in }
    var section: SportSectionToggleUi?
    var toggleSection: (Int64) -> Void = { _ in }

    var body: some View {
        ItmoSheet(title: AppStrings.string("sport_lesson_sheet_title"), height: .tall) {
            VStack(alignment: .leading, spacing: 2) {
                SportKindTag(kind: sheet.kind?.resolved, tone: sheet.kindTone)
                Text(verbatim: sheet.section).font(.itmo(.headlineSmall, emphasized: true))
                Text(verbatim: sheet.date.resolved).font(.itmo(.bodyLarge)).foregroundStyle(.secondary)
            }
            if let seats = sheet.seats {
                SportSeats(seats: seats, booked: sheet.status?.booked == true ? sheet.status?.text.resolved : nil)
            }
            if let status = sheet.status, sheet.seats == nil || !status.booked {
                ItmoStatusPill(pill: ItmoPill(text: status.text.resolved, tone: status.booked ? .ok : .warn))
            }
            if let clash = sheet.clash {
                Label { Text(verbatim: clash.resolved) } icon: { AppSymbol.eventBusy.image }
                    .font(.itmo(.labelLarge, emphasized: true))
                    .foregroundStyle(ItmoTone.warn.color)
                    .padding(.horizontal, ItmoSpacing.content)
                    .padding(.vertical, ItmoSpacing.compact)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ItmoTone.warn.wash, in: .rect(cornerRadius: 14))
                    .accessibilityIdentifier("sport.sheet.clash")
            }
            if let button = sheet.action {
                actionButton(button)
            }
            if let note = sheet.actionNote {
                ItmoGroupNote(text: note.resolved)
            }
            if let section {
                ItmoListGroup {
                    ItmoSwitchRow(
                        title: section.label.resolved,
                        subtitle: section.detail?.resolved,
                        symbol: .eventRepeat,
                        enabled: section.enabled,
                        isOn: Binding(get: { section.on }, set: { _ in toggleSection(section.lessonId) })
                    )
                }
                .accessibilityIdentifier("sport.sheet.section")
            }
            if !sheet.conditions.isEmpty {
                ItmoSectionHeader(title: AppStrings.string("sport_sheet_conditions"))
                VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
                    ForEach(sheet.conditions, id: \.self) { SportCheckRow(check: $0) }
                }
            }
            ItmoSectionHeader(title: AppStrings.string("sport_sheet_where"))
            ItmoListGroup {
                if let teacher = sheet.teacher {
                    ItmoRow(
                        title: teacher.name,
                        subtitle: AppStrings.string("sport_filter_teacher"),
                        symbol: .person,
                        chevron: teacher.isu != nil,
                        action: teacher.isu.map { isu in { openProfile(isu.int32Value) } }
                    )
                }
                if let place = sheet.place {
                    ItmoRow(
                        title: place.title,
                        subtitle: place.subtitle,
                        symbol: .locationOn,
                        chevron: place.mapAddress != nil,
                        action: place.mapAddress.map { address in { openMap(address) } }
                    )
                }
                if let comment = sheet.comment {
                    ItmoRow(title: comment, subtitle: AppStrings.string("sport_sheet_comment"), symbol: .editNote)
                }
                ForEach(sheet.friends, id: \.isu) { friend in
                    ItmoRow(title: friend.name, subtitle: friend.status?.resolved, chevron: true, action: { openProfile(friend.isu) }) {
                        ItmoAvatar(initials: SportFriendStack.initials(friend.name), size: 30)
                    }
                }
                if let share = sheet.share {
                    ItmoRow(title: AppStrings.string("sport_sheet_share"), symbol: .share) { self.share(share) }
                        .accessibilityIdentifier("sport.sheet.share")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.details")
    }

    @ViewBuilder
    private func actionButton(_ button: SportSheetButton) -> some View {
        let send = { act(button.action) }
        Group {
            if button.destructive {
                Button(role: .destructive, action: send) {
                    Text(verbatim: button.label.resolved).frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
            } else {
                Button(action: send) {
                    Text(verbatim: button.label.resolved)
                        .foregroundStyle(ItmoColor.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .itmoTint()
            }
        }
        .font(.itmo(.titleMedium, emphasized: true))
        .disabled(!button.enabled)
        .accessibilityIdentifier("sport.sheet.action")
    }

    private func openMap(_ address: String) {
        guard let actions = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions else { return }
        _ = actions.openMap(destination: MapDestination(label: address, address: address, latitude: nil, longitude: nil))
    }
}

/// The free places large over a bar of seats, taken ones quiet; `booked` adds its pill.
struct SportSeats: View {
    let seats: SportSeatsUi
    let booked: String?

    var body: some View {
        let tone = seats.occupancy.tone.kitTone
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            HStack(spacing: ItmoSpacing.content) {
                Text(verbatim: "\(seats.free)")
                    .font(.system(size: 52, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(tone.color)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: seats.label.resolved).font(.itmo(.titleMedium, emphasized: true))
                    Text(verbatim: seats.detail.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if let booked { ItmoStatusPill(pill: ItmoPill(text: booked, tone: .ok, symbol: .check)).fixedSize() }
            }
            let total = min(Int(seats.occupancy.total), 30)
            let taken = Int(Double(seats.occupancy.share) * Double(total))
            HStack(spacing: 3) {
                ForEach(0..<max(total, 0), id: \.self) { index in
                    RoundedRectangle(cornerRadius: 3).fill(index < taken ? Color(.systemFill) : tone.color).frame(height: 12)
                }
            }
            .accessibilityHidden(true)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A condition: met, pending or not met.
struct SportCheckRow: View {
    let check: SportCheck

    var body: some View {
        HStack(spacing: 10) {
            symbol.image.font(.itmo(.titleMedium)).foregroundStyle(color).accessibilityHidden(true)
            Text(verbatim: check.text.resolved).foregroundStyle(check.state == .wait ? Color.secondary : check.state == .no ? ItmoColor.error : .primary)
        }
        .font(.itmo(.bodyLarge))
    }

    private var symbol: AppSymbol {
        switch check.state {
        case .ok: .checkCircle
        case .wait: .schedule
        case .no: .close
        }
    }

    private var color: Color {
        switch check.state {
        case .ok: ItmoColor.success
        case .wait: .secondary
        case .no: ItmoColor.error
        }
    }
}

/// More filters (DECISIONS.md, Sign-up): the sections, the building, the time and the teacher where the settings show
/// them, the switches, then reset or show.
struct SportFiltersSheet: View {
    let filters: SportFiltersUi
    let viewModel: SportSignViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ItmoSheet(title: AppStrings.string("sport_filters_title"), height: .tall) {
            ItmoSectionHeader(title: AppStrings.string("sport_filter_type"))
            ItmoChips(chips: filters.sections.map(\.kitChip), wrap: true) { index in
                if let value = filters.sections[index].value { viewModel.toggleSection(name: value) }
            }
            if filters.buildings.count > 1 {
                ItmoSectionHeader(title: AppStrings.string("sport_filter_building"))
                ItmoChips(chips: filters.buildings.map(\.kitChip)) { viewModel.selectBuilding(name: filters.buildings[$0].value) }
            }
            if filters.showDayPart {
                ItmoSectionHeader(title: AppStrings.string("sport_filter_time"))
                ItmoSegmentedChoice(
                    label: AppStrings.string("sport_filter_time"),
                    options: [SportDayPart.anytime, .morning, .day, .evening],
                    selection: Binding(get: { filters.dayPart }, set: { viewModel.selectDayPart(part: $0) })
                ) { part in
                    switch part {
                    case .anytime: AppStrings.string("sport_part_any")
                    case .morning: AppStrings.string("sport_part_morning")
                    case .day: AppStrings.string("sport_part_day")
                    case .evening: AppStrings.string("sport_part_evening")
                    }
                }
            }
            if let teachers = filters.teachers {
                ItmoSectionHeader(title: AppStrings.string("sport_filter_teacher"))
                ItmoChips(chips: teachers.map(\.kitChip), wrap: true) { viewModel.selectTeacher(name: teachers[$0].value) }
            }
            ItmoListGroup {
                ItmoSwitchRow(
                    title: AppStrings.string("sport_filter_only_free"),
                    symbol: .check,
                    isOn: Binding(get: { filters.onlyFree }, set: { viewModel.setOnlyFree(on: $0) })
                )
                if let pinned = filters.onlyPinned {
                    ItmoSwitchRow(
                        title: AppStrings.string("sport_filter_only_pinned"),
                        symbol: .keep,
                        isOn: Binding(get: { pinned.boolValue }, set: { viewModel.setOnlyPinned(on: $0) })
                    )
                }
                ItmoSwitchRow(
                    title: AppStrings.string("sport_filter_only_friends"),
                    symbol: .group,
                    isOn: Binding(get: { filters.onlyFriends }, set: { viewModel.setOnlyFriends(on: $0) })
                )
            }
            .padding(.top, ItmoSpacing.compact)
            HStack(spacing: ItmoSpacing.compact) {
                Button { viewModel.resetFilters() } label: {
                    Text(verbatim: AppStrings.string("common_reset")).padding(.vertical, 6)
                }
                .buttonStyle(.glass)
                .controlSize(.large)
                .itmoTint()
                Button { dismiss() } label: {
                    Text(verbatim: filters.shownCount.resolved)
                        .foregroundStyle(ItmoColor.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .itmoTint()
            }
            .font(.itmo(.titleMedium, emphasized: true))
            .padding(.top, ItmoSpacing.compact)
        }
        .accessibilityIdentifier("sport.filters")
    }
}

extension SportOption {
    var kitChip: ItmoChip { ItmoChip(label: label.resolved, selected: selected) }
}

/// Join the queue? (DECISIONS.md, Queue): what and when, the three steps, the late-sign switch of a real
/// lesson, the queues left, then join or not now.
struct SportJoinSheet: View {
    let join: SportPromptJoin
    let confirm: (Bool) -> Void
    @State private var lateSign = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ItmoSheet(title: AppStrings.string("sport_join_title")) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: join.what.resolved).font(.itmo(.titleMedium, emphasized: true))
                Text(verbatim: join.whenText.resolved).font(.itmo(.bodyLarge)).foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(join.steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 14) {
                        Text(verbatim: "\(index + 1)")
                            .font(.itmo(.labelLarge, emphasized: true))
                            .foregroundStyle(ItmoColor.primary)
                            .frame(width: 28, height: 28)
                            .background(ItmoColor.primary.opacity(0.14), in: .circle)
                            .accessibilityHidden(true)
                        Text(verbatim: step.resolved).font(.itmo(.bodyLarge)).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(.vertical, ItmoSpacing.related)
            if join.lateSign {
                ItmoListGroup {
                    ItmoSwitchRow(
                        title: AppStrings.string("sport_join_late"),
                        subtitle: join.lateSignHint?.resolved,
                        symbol: .schedule,
                        isOn: $lateSign
                    )
                }
            }
            if let limit = join.limit {
                ItmoGroupNote(text: limit.resolved)
            }
            Button { confirm(lateSign) } label: {
                Text(verbatim: AppStrings.string("sport_action_queue"))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .font(.itmo(.titleMedium, emphasized: true))
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .itmoTint()
            .padding(.top, ItmoSpacing.compact)
            .accessibilityIdentifier("sport.join.confirm")
            Button { dismiss() } label: {
                Text(verbatim: AppStrings.string("sport_join_later")).frame(maxWidth: .infinity)
            }
            .itmoTint()
        }
        .accessibilityIdentifier("sport.join")
    }
}
