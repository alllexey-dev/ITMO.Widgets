import Foundation
import UserNotifications

/// What the notification service makes of one push (docs/ios.md, Notifications). Backend's alert is the
/// fallback; this decides whether to show it, retitle it with the booking's outcome or drop it.
///
/// - Signed out, the demo or a push for another ISU (`recipient_isu` against session-v1.json): dropped; a sport
///   push without the services opt-in too.
/// - Friendship and a new connection (`notification_device_connected_*`): shown as Backend wrote it (`loc-key`,
///   `loc-args`).
/// - The user's agent booked a lesson: Backend's sport alert with the agent named in the body
///   (`notification_sport_agent_booked`), as Android shows it; nothing is booked here.
/// - Sport: booked as Android's handler books; a booking or MyITMO's refusal retitles the alert with
///   `notification_sport_success` or `_failure` over Android's text; a failure before MyITMO's answer keeps Backend's
///   "a place is free"; a full lesson, or nothing left to book, is dropped, as Android shows nothing. Every booking
///   and refusal is also left for the bell in the App Group.
/// - A type the user turned off in the app (`disabledPushes` of session-v1.json), with `honorsDisabledPushes` only:
///   dropped; a sport push still books and leaves its result for the bell.
struct PushHandler {
    let session: () -> SessionFile?
    let booker: SportPushBooker
    /// Off until the extension holds `com.apple.developer.usernotifications.filtering`: without it iOS shows a dropped
    /// push anyway, the app hides those switches, and a stored "off" the user can no longer change must not turn a
    /// sport outcome back into Backend's alert.
    var honorsDisabledPushes = false
    /// Leaves each booking or refusal for the bell (`SportResultInbox`).
    var record: (SportSignNotice) -> Void = { _ in }

    func handle(_ content: UNNotificationContent) async -> UNNotificationContent {
        guard let message = PushMessage(userInfo: content.userInfo) else { return content }
        guard let session = session(), !session.demo, let isu = session.isu, isu == message.recipientIsu else {
            return Self.dropped
        }
        let muted = honorsDisabledPushes && (message.channel.map(session.disabledPushes.contains) ?? false)
        guard let queue = SportQueue(pushType: message.type) else {
            if muted { return Self.dropped }
            return message.type == PushMessage.agentBooked ? Self.agentBooked(content, payload: message.payload) : content
        }
        guard session.servicesEnabled else { return Self.dropped }

        let booking = await booker.book(queue, payload: message.payload)
        booking.notices.forEach(record)
        if muted { return Self.dropped }
        if let notice = booking.notices.first { return Self.rewritten(content, with: notice) }
        return booking.retryLater ? content : Self.dropped
    }

    /// Empty content: the system shows nothing once the extension holds `usernotifications.filtering`;
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

    /// Android's `SportAgentBookedPayload.toAppNotification`: the section, the Moscow start and the agent; a payload
    /// without them keeps Backend's alert.
    static func agentBooked(_ content: UNNotificationContent, payload: [String: Any]) -> UNNotificationContent {
        guard let lesson = (payload["sportLessons"] as? [Any])?.first.flatMap(PushedLesson.init),
              let agent = (payload["agentName"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              let mutable = content.mutableCopy() as? UNMutableNotificationContent
        else { return content }
        let section = lesson.sectionName.trimmingCharacters(in: .whitespacesAndNewlines)
        mutable.title = String(localized: .notificationSportSuccess)
        mutable.body = String(localized: .notificationSportAgentBooked(section, lessonTime.string(from: lesson.start), agent))
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
            appVersion: clientVersion(bundle),
            client: { clientHeader(bundle, installId: SessionFile.read(fromContainer: container)?.installId) }
        )
        let inbox = SportResultInbox(container: container)
        return PushHandler(
            session: { SessionFile.read(fromContainer: container) },
            booker: SportPushBooker(access: access, calls: calls, now: Date.init),
            record: { inbox.record($0, at: Date()) }
        )
    }

    /// `IosClientVersion`'s `X-App-Version`: `<version> (<build>); ios; dev|sideload|testflight|appstore`.
    static func clientVersion(_ bundle: Bundle) -> String {
        func part(_ key: String) -> String {
            let value = (bundle.object(forInfoDictionaryKey: key) as? String ?? "")
                .filter { $0.isASCII && ("!"..."~").contains($0) && !"();".contains($0) }
            return value.isEmpty ? "unknown" : value
        }
        return "\(part("CFBundleShortVersionString")) (\(part("CFBundleVersion"))); ios; \(distribution(bundle))"
    }

    /// `IosClientVersion.headerFromMainBundle`'s `X-Client` (ADR 0034): `kind=ios; version=<version>; build=<build>;
    /// channel=<distribution>; os=<iOS major>; install=<id>`. [installId] is the app's, from session-v1.json; without
    /// it `install` is left out.
    static func clientHeader(_ bundle: Bundle, installId: String?) -> String {
        func info(_ key: String) -> String? { bundle.object(forInfoDictionaryKey: key) as? String }
        // `IosClientVersion.buildIdentity`: `BuildChannel` custom or the distribution, `BuildSource` and `BuildCommit`
        // as they are; absent keys read as official and unknown.
        return formatClientHeader([
            ("kind", "ios"),
            ("version", info("CFBundleShortVersionString")),
            ("build", info("CFBundleVersion")),
            ("channel", info("BuildChannel") == "custom" ? "custom" : distribution(bundle)),
            ("source", info("BuildSource")),
            ("commit", info("BuildCommit")),
            ("os", String(ProcessInfo.processInfo.operatingSystemVersion.majorVersion)),
            ("install", installId),
        ])
    }

    /// `ClientHeader.format`: `key=value` pairs joined by `; ` in their order; a value keeps only `[A-Za-z0-9._/+-]`
    /// and 64 characters, an empty one leaves its key out, and a pair that would pass 512 characters is left out.
    static func formatClientHeader(_ pairs: [(String, String?)]) -> String {
        pairs.reduce(into: "") { header, pair in
            let value = String((pair.1 ?? "").filter(isClientHeaderCharacter).prefix(64))
            let next = header.isEmpty ? "\(pair.0)=\(value)" : "\(header); \(pair.0)=\(value)"
            if !value.isEmpty && next.utf8.count <= 512 { header = next }
        }
    }

    private static func isClientHeaderCharacter(_ char: Character) -> Bool {
        char.isASCII && (char.isLetter || char.isNumber || "._/+-".contains(char))
    }

    /// `IosClientVersion.distribution`: the App Store and TestFlight strip the provisioning profile; TestFlight
    /// builds have a sandbox receipt, the only synchronous signal of it (`AppTransaction` is async).
    private static func distribution(_ bundle: Bundle) -> String {
        #if DEBUG
        return "dev"
        #else
        if bundle.path(forResource: "embedded", ofType: "mobileprovision") != nil { return "sideload" }
        if bundle.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt" { return "testflight" }
        return "appstore"
        #endif
    }
}
