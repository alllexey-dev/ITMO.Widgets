import Foundation

/// A push's custom keys (Backend `docs/contracts/notifications.md`): `data`, the JSON envelope `{type, payload}` as a
/// string, and `recipient_isu`, the ISU of the account it is for.
struct PushMessage {
    let type: String
    let payload: [String: Any]
    let recipientIsu: Int?

    static let friendship = "FRIENDSHIP_EVENT_PAYLOAD"

    init?(userInfo: [AnyHashable: Any]) {
        guard let text = userInfo["data"] as? String,
              let envelope = (try? JSONSerialization.jsonObject(with: Data(text.utf8))) as? [String: Any],
              let type = envelope["type"] as? String
        else { return nil }
        self.type = type
        payload = envelope["payload"] as? [String: Any] ?? [:]
        switch userInfo["recipient_isu"] {
        case let isu as String: recipientIsu = Int(isu)
        case let isu as NSNumber: recipientIsu = isu.intValue
        default: recipientIsu = nil
        }
    }
}

/// The sport queue a push belongs to; its Backend path segment.
enum SportQueue: String {
    case free = "free-sign"
    case auto = "auto-sign"

    init?(pushType: String) {
        switch pushType {
        case "SPORT_FREE_SIGN_LESSONS_PAYLOAD": self = .free
        case "SPORT_AUTO_SIGN_LESSONS_PAYLOAD": self = .auto
        default: return nil
        }
    }
}

/// The part of a pushed `SportLessonDto` the booking needs.
struct PushedLesson: Equatable {
    let id: Int64
    let sectionName: String
    let start: Date
    let end: Date

    /// Nil for a malformed entry, which costs only itself.
    init?(_ value: Any) {
        guard let entry = value as? [String: Any], let id = (entry["id"] as? NSNumber)?.int64Value,
              let section = entry["sectionName"] as? String,
              let start = (entry["start"] as? String).flatMap(Self.parseOffsetDateTime),
              let end = (entry["end"] as? String).flatMap(Self.parseOffsetDateTime)
        else { return nil }
        self.id = id
        sectionName = section
        self.start = start
        self.end = end
    }

    /// ISO 8601 with an offset where seconds and the fraction are optional: Java writes `2026-10-05T17:00+03:00`
    /// for a lesson on the minute (SP-16a trap).
    static func parseOffsetDateTime(_ text: String) -> Date? {
        guard let match = offsetDateTime.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else {
            return nil
        }
        func group(_ index: Int) -> String? { Range(match.range(at: index), in: text).map { String(text[$0]) } }
        guard let minutes = group(1), let offset = group(4),
              let date = ISO8601DateFormatter().date(from: minutes + (group(2) ?? ":00") + offset)
        else { return nil }
        return date.addingTimeInterval(group(3).flatMap { Double("0" + $0) } ?? 0)
    }

    private static let offsetDateTime = try! NSRegularExpression(
        pattern: #"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(:\d{2})?(\.\d+)?(Z|[+-]\d{2}:\d{2})$"#
    )
}

/// What MyITMO answered to a sign-up: Kotlin's `SportSignOutcome` (KM-11c), golden `sign-outcomes.txt`.
enum SportSignOutcome: String {
    case signedIn = "SIGNED_IN"
    case noCapacity = "NO_CAPACITY"
    case rejected = "REJECTED"
    case retryLater = "RETRY_LATER"

    /// MyITMO's legacy "no free places" refusal, followed by the student and lesson context.
    static let noCapacityMessage = "\u{43d}\u{435}\u{43b}\u{44c}\u{437}\u{44f} \u{437}\u{430}\u{43f}\u{438}\u{441}"
        + "\u{430}\u{442}\u{44c} \u{441}\u{442}\u{443}\u{434}\u{435}\u{43d}\u{442}\u{430}: \u{43d}\u{435}\u{442} "
        + "\u{441}\u{432}\u{43e}\u{431}\u{43e}\u{434}\u{43d}\u{44b}\u{445} \u{43c}\u{435}\u{441}\u{442} \u{43d}\u{430} "
        + "\u{437}\u{430}\u{43d}\u{44f}\u{442}\u{438}\u{438}"

    /// Only MyITMO's own refusal on a 2xx (an error envelope, as code 137) decides about the lesson; another status,
    /// an unreadable answer or a success without `result` is retried by Backend later.
    init(status: Int, body: Data) {
        guard (200..<300).contains(status),
              let envelope = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        else { self = .retryLater; return }
        let code = (envelope["error_code"] as? NSNumber)?.intValue ?? 0
        guard code != 0 else {
            self = envelope["result"].map { $0 is NSNull ? SportSignOutcome.retryLater : .signedIn } ?? .retryLater
            return
        }
        let message = envelope["error_message"] as? String ?? ""
        let context = message.replacingOccurrences(of: Self.noCapacityMessage, with: "")
        self = message.contains(Self.noCapacityMessage) && context.utf16.count > 20 ? .noCapacity : .rejected
    }
}

/// A booked or refused lesson: what the notification tells.
struct SportSignNotice: Equatable {
    let lessonId: Int64
    let sectionName: String
    let start: Date
    let booked: Bool
}

/// The calls of a sport push: MyITMO's sign-up and Backend's settle, both with the ITMO.ID access token.
struct SportPushCalls {
    let transport: PushTransport
    let backend: URL?
    let appVersion: String

    static let myItmo = URL(string: "https://my.itmo.ru")!

    /// POST `/api/sport/sign/schedule/lessons` with `[id]`; a transport failure is `retryLater`.
    func signIn(_ lesson: Int64, token: String) async -> SportSignOutcome {
        var request = URLRequest.push(
            "POST", Self.myItmo.appendingPathComponent("api/sport/sign/schedule/lessons"),
            body: Data("[\(lesson)]".utf8), contentType: "application/json"
        )
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        guard let answer = try? await transport.send(request) else { return .retryLater }
        return SportSignOutcome(status: answer.status, body: answer.body)
    }

    /// POST `/api/sport/<queue>/lesson/<id>/<action>` on Backend (`mark-satisfied` or `cancel`); best effort, as
    /// Android's booker: a failure is left to Backend's next attempt.
    func settle(_ queue: SportQueue, lesson: Int64, action: String, token: String) async {
        guard let backend else { return }
        var request = URLRequest.push(
            "POST", backend.appendingPathComponent("api/sport/\(queue.rawValue)/lesson/\(lesson)/\(action)")
        )
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(appVersion, forHTTPHeaderField: "X-App-Version")
        _ = try? await transport.send(request)
    }
}

/// Android's `SportSignPushBooker` (KM-11c) in the notification service: each lesson decoded on its own and skipped
/// when malformed, unnamed, repeated or already ended, then booked on MyITMO. A booking is reported and marked
/// satisfied on Backend, MyITMO's refusal is reported and cancels the queue entry, a full lesson stays queued, any
/// other failure is retried by Backend. Bookings, pending queues and widgets refresh when the app runs next.
struct SportPushBooker {
    let access: MyItmoAccess
    let calls: SportPushCalls
    let now: () -> Date

    struct Booking: Equatable {
        var notices: [SportSignNotice] = []
        var retryLater = false
    }

    func book(_ queue: SportQueue, payload: [String: Any]) async -> Booking {
        var result = Booking()
        guard let lessons = payload["sportLessons"] as? [Any] else { return result }
        var seen = Set<Int64>()
        for value in lessons.prefix(100) {
            guard !Task.isCancelled else { break }
            guard let lesson = PushedLesson(value), lesson.id > 0, seen.insert(lesson.id).inserted,
                  lesson.end > now()
            else { continue }
            let section = lesson.sectionName.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !section.isEmpty else { continue }
            guard let token = try? await access.validAccessToken() else {
                result.retryLater = true
                continue
            }
            func notice(booked: Bool) -> SportSignNotice {
                SportSignNotice(lessonId: lesson.id, sectionName: section, start: lesson.start, booked: booked)
            }
            switch await calls.signIn(lesson.id, token: token) {
            case .signedIn:
                result.notices.append(notice(booked: true))
                await calls.settle(queue, lesson: lesson.id, action: "mark-satisfied", token: token)
            case .rejected:
                result.notices.append(notice(booked: false))
                await calls.settle(queue, lesson: lesson.id, action: "cancel", token: token)
            case .noCapacity:
                break
            case .retryLater:
                result.retryLater = true
            }
        }
        return result
    }
}
