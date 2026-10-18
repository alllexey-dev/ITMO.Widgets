import Foundation

/// Where a widget tap goes (`RouteURL`, docs/ios.md, Routes): placed widgets keep these URLs, so the ids and the
/// query names never change (`StableIdentifiersTests`).
enum WidgetRoute {
    /// The schedule on today: the day widget's header and a lesson without arguments.
    static let today = url("today")
    /// The sport tab: the sport widget.
    static let sport = url("sport")
    /// The pass sheet over the home tab: the QR widget.
    static let pass = url("qr_pass")

    /// The query item of a lesson route: the lesson's `LessonDetailsArgs` JSON in base64url.
    static let lessonArguments = "args"
    /// The query items of a sport route: the lesson id and `1` for a predicted lesson.
    static let sportLesson = "lesson"
    static let sportPredicted = "predicted"

    /// What a lesson row opens: its lesson sheet, the queued sport lesson in the sport tab, or the schedule on today.
    static func lesson(_ lesson: WidgetLesson) -> URL {
        if let sport = lesson.sport {
            return sportLesson(id: abs(sport.lessonId), predicted: sport.isPrediction)
        }
        guard let json = lesson.lesson?.text else { return today }
        var components = URLComponents(url: url("lesson"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: lessonArguments, value: base64URL(Data(json.utf8)))]
        return components.url ?? today
    }

    /// The sport tab on the lesson `id`, the catalog lesson a `predicted` booking repeats.
    static func sportLesson(id: Int64, predicted: Bool) -> URL {
        var components = URLComponents(url: sport, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: sportLesson, value: String(id))]
            + (predicted ? [URLQueryItem(name: sportPredicted, value: "1")] : [])
        return components.url ?? sport
    }

    /// RFC 4648 base64url without padding, safe in a query.
    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// The bytes of a base64url text, with or without padding.
    static func data(base64URL text: String) -> Data? {
        var base64 = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        return Data(base64Encoded: base64)
    }

    private static func url(_ id: String) -> URL {
        URL(string: "itmowidgets://route/\(id)")!
    }
}
