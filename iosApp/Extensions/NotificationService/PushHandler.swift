import Foundation
import UserNotifications

/// What the notification service makes of one push (IO-12a, docs/ios.md, Notifications). Backend's alert is the
/// fallback; this decides whether to show it, retitle it with the booking's outcome or drop it.
///
/// - Signed out, the demo or a push for another ISU (`recipient_isu` against session-v1.json): dropped; a sport
///   push without the services opt-in too.
/// - Friendship: shown as Backend wrote it (`loc-key`, `loc-args`).
/// - Sport: booked as Android's handler books; a booking or MyITMO's refusal retitles the alert with
///   `notification_sport_success` or `_failure` over Android's text; a failure before MyITMO's answer keeps Backend's
///   "a place is free"; a full lesson, or nothing left to book, is dropped, as Android shows nothing.
struct PushHandler {
    let session: () -> SessionFile?
    let booker: SportPushBooker

    func handle(_ content: UNNotificationContent) async -> UNNotificationContent {
        guard let message = PushMessage(userInfo: content.userInfo) else { return content }
        guard let session = session(), !session.demo, let isu = session.isu, isu == message.recipientIsu else {
            return Self.dropped
        }
        if message.type == PushMessage.friendship { return content }
        guard let queue = SportQueue(pushType: message.type) else { return content }
        guard session.servicesEnabled else { return Self.dropped }

        let booking = await booker.book(queue, payload: message.payload)
        if let notice = booking.notices.first { return Self.rewritten(content, with: notice) }
        return booking.retryLater ? content : Self.dropped
    }

    /// Empty content: the system shows nothing once the extension holds `usernotifications.filtering` (after T13);
    /// until then the push is not hidden (docs/ios.md, Degradations).
    static let dropped = UNNotificationContent()

    /// Android's sport notice (`SportSignNotice.toAppNotification`): the outcome as the title, the section and the
    /// Moscow start as the body (golden `push.txt`). Thread, collapse id and `userInfo` stay Backend's.
    static func rewritten(_ content: UNNotificationContent, with notice: SportSignNotice) -> UNNotificationContent {
        guard let mutable = content.mutableCopy() as? UNMutableNotificationContent else { return content }
        mutable.title = String(localized: notice.booked ? .notificationSportSuccess : .notificationSportFailure)
        let start = lessonTime.string(from: notice.start)
        mutable.body = String(localized: .notificationSportLesson(notice.sectionName, start))
        return mutable
    }

    /// `DateTexts.DAY_SHORT_MONTH_TIME` in Europe/Moscow: the Russian short genitive month of ICU is Android's list.
    static let lessonTime: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.timeZone = TimeZone(identifier: "Europe/Moscow")
        formatter.dateFormat = "d MMM, HH:mm"
        return formatter
    }()
}

extension PushHandler {
    /// The handler of this process: the App Group of its Info.plist, the shared Keychain session, Backend's origin.
    static func live(bundle: Bundle = .main) -> PushHandler {
        let container = AppGroupSnapshot.container(bundle: bundle)
        let transport = URLSessionPushTransport()
        let access = MyItmoAccess(
            item: KeychainTokenItem(),
            lock: container.map { FileLock(file: $0.appendingPathComponent("locks/myitmo-refresh.lock")) },
            transport: transport,
            now: Date.init
        )
        let calls = SportPushCalls(
            transport: transport,
            backend: (bundle.object(forInfoDictionaryKey: "BackendBaseURL") as? String).flatMap(URL.init(string:)),
            appVersion: clientVersion(bundle)
        )
        return PushHandler(
            session: { SessionFile.read(fromContainer: container) },
            booker: SportPushBooker(access: access, calls: calls, now: Date.init)
        )
    }

    /// `IosClientVersion`'s `X-App-Version`: `<version> (<build>); ios; dev|appstore`.
    static func clientVersion(_ bundle: Bundle) -> String {
        func part(_ key: String) -> String {
            let value = (bundle.object(forInfoDictionaryKey: key) as? String ?? "")
                .filter { $0.isASCII && ("!"..."~").contains($0) && !"();".contains($0) }
            return value.isEmpty ? "unknown" : value
        }
        #if DEBUG
        let distribution = "dev"
        #else
        let distribution = "appstore"
        #endif
        return "\(part("CFBundleShortVersionString")) (\(part("CFBundleVersion"))); ios; \(distribution)"
    }
}
