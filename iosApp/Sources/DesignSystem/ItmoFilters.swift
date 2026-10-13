import SwiftUI

// Filters and pickers of a list (docs/design.md, SwiftUI screens): the search field, filter chips, the week strip,
// a switch row of a group and the places taken. Texts reach the kit resolved.

/// One filter chip: the label, an optional quiet count, selected with a check.
struct ItmoChip: Hashable {
    let label: String
    var selected = false
    var count: Int?
}

/// Filter chips: a row that scrolls sideways, or lines that wrap (a filter set in a sheet). `tap` gets the index.
struct ItmoChips: View {
    let chips: [ItmoChip]
    var wrap = false
    let tap: (Int) -> Void

    var body: some View {
        if wrap {
            ItmoFlow(spacing: ItmoSpacing.compact) { buttons }
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: ItmoSpacing.compact) { buttons }
                    .padding(.horizontal, ItmoKit.margin)
            }
            .padding(.horizontal, -ItmoKit.margin)
        }
    }

    private var buttons: some View {
        ForEach(chips.indices, id: \.self) { index in
            let chip = chips[index]
            Button { tap(index) } label: {
                HStack(spacing: ItmoSpacing.related) {
                    if chip.selected { AppSymbol.check.image.font(.itmo(.labelSmall, emphasized: true)) }
                    Text(verbatim: chip.label)
                    if let count = chip.count {
                        Text(verbatim: "\(count)").foregroundStyle(.secondary).monospacedDigit()
                    }
                }
                .font(.itmo(.labelLarge))
                .padding(.horizontal, ItmoSpacing.content)
                .padding(.vertical, ItmoSpacing.compact)
                .foregroundStyle(chip.selected ? ItmoColor.primary : Color.primary)
                .background(chip.selected ? ItmoColor.primary.opacity(0.14) : Color(.tertiarySystemFill), in: .capsule)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(chip.selected ? .isSelected : [])
        }
    }
}

/// The search of a list in its content: the magnifier, the field, a clear button while there is a query and an
/// optional trailing action (more filters) with a badge of what it narrows.
struct ItmoSearchField: View {
    let placeholder: String
    @Binding var query: String
    var clearLabel: String
    var action: (symbol: AppSymbol, label: String, badge: Int, run: () -> Void)?

    var body: some View {
        HStack(spacing: ItmoSpacing.compact) {
            AppSymbol.search.image.foregroundStyle(.secondary).accessibilityHidden(true)
            TextField(text: $query, prompt: Text(verbatim: placeholder)) { Text(verbatim: placeholder) }
                .textFieldStyle(.plain)
                .submitLabel(.search)
            if !query.isEmpty {
                Button { query = "" } label: { AppSymbol.close.image.foregroundStyle(.secondary) }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(Text(verbatim: clearLabel))
            }
            if let action {
                Button(action: action.run) {
                    action.symbol.image
                        .overlay(alignment: .topTrailing) {
                            if action.badge > 0 {
                                Text(verbatim: "\(action.badge)")
                                    .font(.itmo(.labelSmall, emphasized: true))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 4)
                                    .frame(minWidth: 16, minHeight: 16)
                                    .background(ItmoColor.error, in: .capsule)
                                    .offset(x: 9, y: -7)
                            }
                        }
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.borderless)
                .itmoTint()
                .accessibilityLabel(Text(verbatim: action.label))
                .accessibilityValue(Text(verbatim: action.badge > 0 ? "\(action.badge)" : ""))
            }
        }
        .font(.itmo(.bodyLarge))
        .padding(.leading, ItmoSpacing.content)
        .padding(.trailing, action == nil ? ItmoSpacing.content : ItmoSpacing.related)
        .frame(minHeight: 44)
        .background(Color(.tertiarySystemFill), in: .capsule)
    }
}

/// A day of `ItmoWeekStrip`: the weekday over the date, a dot per lesson (at most five).
struct ItmoWeekDay: Hashable {
    let id: String
    let weekday: String
    let number: String
    let lessons: Int
    var today = false
    var past = false
    var selected = false
    /// What VoiceOver reads for the day.
    var label: String
}

/// One week as seven equal targets; the selected day sits on the tint. Weeks page sideways in a paging scroll view.
struct ItmoWeekStrip: View {
    let days: [ItmoWeekDay]
    let tap: (Int) -> Void

    var body: some View {
        HStack(spacing: ItmoSpacing.related) {
            ForEach(days.indices, id: \.self) { index in
                let day = days[index]
                Button { tap(index) } label: {
                    VStack(spacing: 2) {
                        Text(verbatim: day.weekday).font(.itmo(.labelMedium))
                        Text(verbatim: day.number)
                            .font(.itmo(.titleLarge, emphasized: day.today || day.selected))
                            .monospacedDigit()
                        HStack(spacing: 2) {
                            ForEach(0..<min(day.lessons, 5), id: \.self) { _ in
                                Circle().frame(width: 4, height: 4)
                                    .foregroundStyle(day.selected ? ItmoColor.onPrimary : ItmoColor.primary.opacity(day.past ? 0.4 : 0.8))
                            }
                        }
                        .frame(height: 5)
                    }
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .foregroundStyle(color(day))
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.vertical, ItmoSpacing.compact)
                    .background(day.selected ? ItmoColor.primary : .clear, in: .rect(cornerRadius: 16))
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: day.label))
                .accessibilityAddTraits(day.selected ? .isSelected : [])
                .accessibilityIdentifier("kit.weekDay.\(day.id)")
            }
        }
    }

    private func color(_ day: ItmoWeekDay) -> Color {
        if day.selected { return ItmoColor.onPrimary }
        if day.today { return ItmoColor.primary }
        return day.past ? .secondary : .primary
    }
}

/// A row of a group with a trailing switch; the whole row toggles.
struct ItmoSwitchRow: View {
    let title: String
    var subtitle: String?
    var symbol: AppSymbol?
    var enabled = true
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: ItmoSpacing.content) {
                if let symbol {
                    symbol.image.foregroundStyle(ItmoColor.primary).frame(width: 30).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: title).font(.itmo(.bodyLarge))
                    if let subtitle {
                        Text(verbatim: subtitle).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .disabled(!enabled)
        .itmoTint()
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
    }
}

/// Places taken: a short bar and "22 of 24", both in the tone.
struct ItmoOccupancy: View {
    let text: String
    let share: Double
    let tone: ItmoTone

    @Environment(\.itmoTrack) private var track

    var body: some View {
        HStack(spacing: 6) {
            Capsule()
                .fill(track)
                .frame(width: 44, height: 6)
                .overlay(alignment: .leading) {
                    Capsule().fill(tone.color).frame(width: 44 * min(max(share, 0), 1))
                }
                .accessibilityHidden(true)
            Text(verbatim: text).font(.itmo(.labelMedium, emphasized: true)).foregroundStyle(tone.color).monospacedDigit()
        }
    }
}
