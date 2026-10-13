import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The screen kit in all four appearances: the lesson timeline and card, pills, the segmented choice, cards and
/// sections, row groups and the gated, action, lock and stale states. Texts are synthetic, in the words of the
/// design lab (vibe/design-lab/ux-native). Drawn in a window: glass buttons and the system's unavailable view need it.
@MainActor
final class KitSnapshotTests: XCTestCase {
    func testTimelineNow() {
        let day = KitColumn {
            ItmoSectionHeader(title: "Пары", count: "5 пар")
            ItmoLessonTimeline(rows: KitFixtures.rows(KitFixtures.day(at: 12 * 60 + 22), now: 12 * 60 + 22), showsNow: true)
        }
        assertScreenAppearances(of: day, named: "now", height: nil, settle: 0.3)
    }

    /// Another day: no now marker, done lessons count as elapsed.
    func testTimelineWithoutNow() {
        let day = KitFixtures.day(at: 13 * 60 + 40)
        let timeline = ItmoLessonTimeline(rows: KitFixtures.rows(day, now: nil))
            .padding(ItmoKit.margin)
            .background(Color(.systemGroupedBackground))
        assertScreenAppearances(of: timeline, named: "day", appearances: [.light, .dark], height: nil, settle: 0.3)
    }

    func testLessonCards() {
        let lessons = KitFixtures.day(at: 12 * 60 + 22)
        var cancelled = lessons[3]
        cancelled.cancelled = true
        cancelled.pills = [ItmoPill(text: "отменена", tone: .bad)]
        let long = ItmoLesson(
            id: "long", start: 18 * 60 + 40, end: 20 * 60 + 10, subject: SnapshotFixtures.longSubjectName,
            type: .assessment, kind: "Экзамен", teacher: SnapshotFixtures.longPersonName, room: "1404",
            building: "Биржа", note: SnapshotFixtures.longSubjectName
        )
        let cards = KitColumn {
            ItmoLessonCard(lesson: lessons[2])
            ItmoLessonCard(lesson: lessons[1])
            ItmoLessonCard(lesson: cancelled)
            ItmoLessonCard(lesson: lessons[4])
            ItmoLessonCard(lesson: long)
        }
        assertScreenAppearances(of: cards, named: "cards", height: nil, settle: 0.3)
    }

    func testPillsAndAvatars() {
        let pills = ItmoFlow {
            ItmoStatusPill(pill: ItmoPill(text: "идёт", tone: .primary))
            ItmoStatusPill(pill: ItmoPill(text: "далее"))
            ItmoStatusPill(pill: ItmoPill(text: "изменена", tone: .warn))
            ItmoStatusPill(pill: ItmoPill(text: "отменена", tone: .bad))
            ItmoStatusPill(pill: ItmoPill(text: "очередь 2 из 5", tone: .warn))
            ItmoStatusPill(pill: ItmoPill(text: "записаны", tone: .ok))
            ItmoStatusPill(pill: ItmoPill(text: "онлайн", symbol: .videocam))
            ItmoStatusPill(pill: ItmoPill(text: "зачёт", tone: .tertiary))
            ItmoAvatar(initials: "ИК")
            ItmoAvatar(initials: "ПЕ", size: 28, tone: .primary)
        }
        .padding(ItmoKit.margin)
        assertScreenAppearances(of: pills, named: "pills", height: nil, settle: 0.3)
    }

    func testSegmentedChoice() {
        let choices = VStack(spacing: ItmoSpacing.group) {
            ItmoSegmentedChoice(label: "Раздел", options: ["Баллы", "Пары", "Материалы"], selection: .constant("Пары")) {
                $0
            }
            ItmoSegmentedChoice(
                label: "Период", options: ["Семестр", "Сессия"], selection: .constant("Семестр"), enabled: false
            ) { $0 }
        }
        .padding(ItmoKit.margin)
        assertScreenAppearances(of: choices, named: "choice", height: nil, settle: 0.3)
    }

    func testCardsAndRows() {
        let screen = KitColumn {
            ItmoCard(tone: .primary, hero: true) {
                Text(verbatim: "Сейчас").font(.itmo(.bodyMedium, emphasized: true)).foregroundStyle(ItmoColor.primary)
                Text(verbatim: "Программирование").font(.itmo(.titleLarge, emphasized: true))
            }
            ItmoSectionHeader(title: "Друзья", count: "3", action: ("Все", {}))
            ItmoListGroup {
                ItmoRow(title: "Заявки в друзья", symbol: .group, tone: .primary, badge: 2, chevron: true)
                ItmoRow(title: "Подключение к ITMO.Widgets", subtitle: "Включено", symbol: .info, tone: .ok, chevron: true)
                ItmoRow(title: "Тема", symbol: .settings, trailing: "Как в системе")
                ItmoRow(title: "Моё", symbol: .check).accessory { AppSymbol.check.image.foregroundStyle(ItmoColor.primary) }
                ItmoRow(title: "Удалить аккаунт", symbol: .delete, tone: .bad, danger: true)
            }
            ItmoGroupNote(text: "Друзья видят ваше расписание, если вы разрешили.")
            ItmoListGroup(inset: 68) {
                ItmoRow(title: "Козлов Иван", subtitle: "на паре до 13:10", chevron: true) { ItmoAvatar(initials: "ИК") }
                ItmoRow(title: SnapshotFixtures.longPersonName, subtitle: "M3204") { ItmoAvatar(initials: "КА") }
            }
        }
        assertScreenAppearances(of: screen, named: "rows", height: nil, settle: 0.3)
    }

    func testGatedActionLockAndStale() {
        let states = KitColumn {
            ItmoGateCard(
                title: "Материалы недоступны",
                reason: "Ссылки и чаты приходят от ITMO.Widgets, а подключение выключено",
                fix: ("Подключить", {})
            )
            ItmoActionCard(
                title: "Войдите в БАРС заново",
                reason: "Сессия закончилась, баллы БАРС не обновляются с 09:40",
                action: ("Войти", {}),
                symbol: .login,
                tone: .warn
            )
            ItmoLockNote(title: "Расписание скрыто", reason: "Иван показывает расписание только друзьям")
            ItmoStale(since: "Данные на 11:44") {
                ItmoLessonCard(lesson: KitFixtures.day(at: 12 * 60 + 22)[2])
            }
        }
        assertScreenAppearances(of: states, named: "states", height: nil, settle: 0.3)
    }

    func testEmptyWithAction() {
        let view = ItmoEmptyView(
            symbol: .eventNote,
            title: "Пар нет",
            description: "На этот день занятий нет.",
            action: ("Открыть расписание", {})
        )
        assertScreenAppearances(of: view, named: "empty", height: nil, settle: 0.3)
    }
}

/// A screen's column without the scroll view of `ItmoScreenStack`, so the window fits its content.
private struct KitColumn<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoKit.gap) { content }
            .padding(.horizontal, ItmoKit.margin)
            .padding(.vertical, ItmoSpacing.group)
            .background(Color(.systemGroupedBackground))
    }
}

/// The design lab's Monday (BRIEF.md, mock data) as kit lessons.
enum KitFixtures {
    /// "Окно 2 ч 10 мин" for 130 minutes.
    static func gap(_ minutes: Int) -> String {
        "Окно " + [minutes / 60 > 0 ? "\(minutes / 60) ч" : nil, minutes % 60 > 0 ? "\(minutes % 60) мин" : nil]
            .compactMap { $0 }.joined(separator: " ")
    }

    /// The timeline rows of `lessons` at `now` (minutes since midnight), as `LessonTimelines` computes them in the
    /// app: the rail under a dot spans the lesson and the short break after it; without `now` done lessons count as
    /// elapsed. Gaps of an hour or more get a row.
    static func rows(_ lessons: [ItmoLesson], now: Int?) -> [ItmoTimelineRow] {
        func share(_ time: Int, _ from: Int, _ to: Int) -> CGFloat {
            guard to > from else { return time >= to ? 1 : 0 }
            return min(max(CGFloat(time - from) / CGFloat(to - from), 0), 1)
        }
        func elapsed(_ lesson: ItmoLesson) -> Bool { lesson.phase == .done || lesson.phase == .now }
        var rows: [ItmoTimelineRow] = []
        for (index, lesson) in lessons.enumerated() {
            if index > 0 {
                let from = lessons[index - 1].end
                if lesson.start - from >= 60 {
                    let progress = now.map { share($0, from, lesson.start) } ?? (elapsed(lesson) ? 1 : 0)
                    rows.append(.gap(id: "gap-\(lesson.id)", label: gap(lesson.start - from), progress: progress))
                }
            }
            let next = index + 1 < lessons.count ? lessons[index + 1].start : nil
            let until = next.map { $0 - lesson.end < 60 ? $0 : lesson.end } ?? lesson.end
            let progress = now.map { share($0, lesson.start, until) } ?? (lesson.phase == .done ? 1 : 0)
            rows.append(.lesson(lesson, progress: progress, reached: now.map { $0 >= lesson.start } ?? elapsed(lesson)))
        }
        return rows
    }

    /// The day with each lesson's phase and pills at `now`.
    static func day(at now: Int) -> [ItmoLesson] {
        var lessons = [
            ItmoLesson(
                id: "1", start: 8 * 60 + 20, end: 9 * 60 + 50, subject: "Математический анализ", type: .lecture,
                kind: "Лекция", teacher: "Лебедев А. С.", room: "1506", building: "Кронва"
            ),
            ItmoLesson(
                id: "2", start: 10 * 60, end: 11 * 60 + 30, subject: "Физика", type: .practice, kind: "Практика",
                teacher: "Смирнова О. П.", room: "2202", building: "Ломо", was: "1506",
                changeNote: "Изменено вчера в 20:14"
            ),
            ItmoLesson(
                id: "3", start: 11 * 60 + 40, end: 13 * 60 + 10, subject: "Программирование", type: .lab,
                kind: "Лабораторная", teacher: "Ершов Н. А.", room: "1506", building: "Кронва", friends: ["ИК"]
            ),
            ItmoLesson(
                id: "4", start: 15 * 60 + 20, end: 16 * 60 + 50, subject: "Английский язык", type: .practice,
                kind: "Практика", teacher: "Петрова А. В.", note: "Пароль: 1234"
            ),
            ItmoLesson(
                id: "5", start: 17 * 60, end: 18 * 60 + 30, subject: "Плавание", type: .sport, kind: "Спорт",
                teacher: "Захаров Н. И.", room: "бассейн", building: "Ломо", friends: ["ПЕ"]
            ),
        ]
        var nextSeen = false
        for index in lessons.indices {
            let lesson = lessons[index]
            let phase: ItmoLesson.Phase
            if lesson.end <= now {
                phase = .done
            } else if lesson.start <= now {
                phase = .now
            } else if !nextSeen {
                phase = .next
                nextSeen = true
            } else {
                phase = .upcoming
            }
            lessons[index].phase = phase
            var pills: [ItmoPill] = []
            if phase == .now { pills.append(ItmoPill(text: "идёт", tone: .primary)) }
            if phase == .next { pills.append(ItmoPill(text: "далее")) }
            if lesson.was != nil { pills.append(ItmoPill(text: "изменена", tone: .warn)) }
            if index == 4 { pills.append(ItmoPill(text: "записаны", tone: .ok)) }
            if lesson.room == nil { pills.append(ItmoPill(text: "онлайн", symbol: .videocam)) }
            lessons[index].pills = pills
        }
        return lessons
    }
}
