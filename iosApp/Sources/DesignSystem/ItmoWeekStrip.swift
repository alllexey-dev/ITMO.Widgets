import SwiftUI

/// A day of an `ItmoWeekStrip`: `weekday` "mon" over `number` "7", a dot per lesson up to `lessons` (at most five),
/// the last in the warning colour when `changed`; `today` in the accent, `past` quieter.
struct ItmoWeekDay: Hashable {
    let weekday: String
    let number: String
    var lessons = 0
    var changed = false
    var today = false
    var past = false
}

/// One week, Monday to Sunday, as seven equal targets; the `selected` day sits on the accent. A paged `TabView` of
/// these is the schedule's week strip, which swipes by weeks; a tap reports the day's index.
struct ItmoWeekStrip: View {
    let days: [ItmoWeekDay]
    var selected: Int?
    var onDay: (Int) -> Void = { _ in }

    var body: some View {
        HStack(spacing: 4) {
            ForEach(days.indices, id: \.self) { index in
                Button { onDay(index) } label: { cell(days[index], selected: index == selected) }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(index == selected ? .isSelected : [])
            }
        }
    }

    private func cell(_ day: ItmoWeekDay, selected: Bool) -> some View {
        let content: Color = selected ? .white : day.today ? ItmoColor.primary : day.past ? .secondary : .primary
        return VStack(spacing: 2) {
            Text(verbatim: day.weekday).font(.itmo(.labelMedium))
            Text(verbatim: day.number)
                .font(.itmo(.titleLarge, emphasized: day.today || selected))
                .monospacedDigit()
                .lineLimit(1)
            HStack(spacing: 2) {
                let dots = min(day.lessons, 5)
                ForEach(0..<dots, id: \.self) { index in
                    Circle()
                        .fill(day.changed && index == dots - 1 ? ItmoTone.warn.color : selected ? .white : ItmoColor.primary.opacity(day.past ? 0.4 : 0.8))
                        .frame(width: 5, height: 5)
                }
                if dots == 0 && day.changed {
                    Circle().fill(ItmoTone.warn.color).frame(width: 5, height: 5)
                }
            }
            .frame(height: 5)
        }
        .foregroundStyle(content)
        .frame(maxWidth: .infinity, minHeight: 44)
        .padding(.vertical, ItmoSpacing.compact)
        .background(selected ? ItmoColor.primary : .clear, in: .rect(cornerRadius: 16))
        .contentShape(.rect)
    }
}
