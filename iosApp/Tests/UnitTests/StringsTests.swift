@testable import ITMOWidgets
import Shared
import UIKit
import XCTest

/// The catalog on iOS: Russian text and Russian plural rules on an English simulator from the string tables, which
/// SwiftUI, the extensions and Kotlin (through `AppPlatform.resolveText`) all read, and every icon of the registry.
///
/// The app process runs `AppLocale.install()` in `App.init`; checks of the native path put English back first through
/// `withEnglishLanguages`, as an extension process without Kotlin sees the device.
@MainActor
final class StringsTests: XCTestCase {
    private let lessonCounts = [5: "5 пар", 21: "21 пара", 2: "2 пары", 11: "11 пар"]

    func testBundleIsRussianOnly() throws {
        let info = try XCTUnwrap(Bundle.main.infoDictionary)
        XCTAssertEqual(info["CFBundleDevelopmentRegion"] as? String, "ru")
        XCTAssertEqual(Bundle.main.localizations.filter { $0 != "Base" }, ["ru"])
    }

    func testStringCatalogSymbolsUseRussianPluralRules() {
        withEnglishLanguages {
            for (count, expected) in lessonCounts {
                XCTAssertEqual(String(localized: .StringsCommon.scheduleLessonCount(count)), expected)
            }
        }
    }

    func testSwiftResolverUsesRussianPluralRules() {
        withEnglishLanguages {
            for (count, expected) in lessonCounts {
                XCTAssertEqual(AppStrings.plural("schedule_lesson_count", count: count), expected)
            }
        }
    }

    func testPluralWithoutPlaceholderSelectsByCount() {
        // The forms show no number; the `count` substitution still takes argument 1.
        withEnglishLanguages {
            XCTAssertEqual(AppStrings.plural("sport_seats_free", count: 5), "мест свободно")
            XCTAssertEqual(AppStrings.plural("sport_seats_free", count: 21), "место свободно")
            XCTAssertEqual(String(localized: .StringsSport.sportSeatsFree(count: 2)), "места свободны")
        }
    }

    func testLocKeyResolvesFromLocalizable() {
        // APNs looks a `loc-key` up in Localizable only.
        XCTAssertEqual(
            Bundle.main.localizedString(forKey: "notification_sport_success", value: nil, table: nil),
            "Вы записаны на спорт"
        )
        XCTAssertEqual(String(localized: .iosWidgetQrReveal), "Нажмите, чтобы показать")
    }

    func testFileTablesFollowLocalizable() {
        XCTAssertEqual(AppStrings.string("qr_pass_title"), "QR-пропуск")
        XCTAssertEqual(AppStrings.string("ios_control_qr_description"), "Открывает пропуск")
    }

    func testArgumentsTakeStringsAndKotlinNumbers() {
        let text = UiTextRes(key: "marks_subjects_more", arguments: ["Физика", 2])

        XCTAssertEqual(text.resolved, "Физика и ещё\u{00A0}2")
    }

    func testNestedUiTextResolvesFirstLikeKotlin() {
        let count = UiTextPlural(key: "schedule_lesson_count", count: 21, arguments: [21])
        let text = UiTextRes(key: "schedule_change_added", arguments: [count])

        XCTAssertEqual(text.resolved, "Добавлена: 21 пара")
    }

    func testJoinedAndLowercaseFirstResolveLikeKotlin() {
        let joined = UiTextJoined(parts: [UiTextDynamic(value: "1506"), UiTextDynamic(value: "Кронва")], separator: " · ")

        XCTAssertEqual(joined.resolved, "1506 · Кронва")
        XCTAssertEqual(UiTextLowercaseFirst(text: UiTextDynamic(value: "Пара")).resolved, "пара")
    }

    /// Kotlin's text outside the screens (notifications, widget files, shares) comes from the same tables.
    func testKotlinResolverReadsTheStringTables() async throws {
        let resolver = try XCTUnwrap(IosKoin.shared.get(protocol: UiTextResolver.self) as? UiTextResolver)
        let text = UiTextRes(key: "schedule_change_added", arguments: [
            UiTextPlural(key: "schedule_lesson_count", count: 5, arguments: [5]),
        ])

        let resolved = try await resolver.resolve(text: text)

        XCTAssertEqual(resolved, "Добавлена: 5 пар")
    }

    func testEverySharedIconHasASymbol() {
        for icon in AppIcon.allCases {
            let symbol = icon.symbol
            let image = symbol.isCustom ? UIImage(named: symbol.systemName) : UIImage(systemName: symbol.systemName)
            XCTAssertNotNil(image, icon.id)
        }
    }

    func testEverySymbolLoads() {
        for symbol in AppSymbol.allCases {
            let image = symbol.isCustom ? UIImage(named: symbol.systemName) : UIImage(systemName: symbol.systemName)
            XCTAssertNotNil(image, "\(symbol.rawValue) -> \(symbol.systemName)")
        }
    }

    /// Runs [body] with English as the process's preferred language, then restores the app's override.
    private func withEnglishLanguages(_ body: () -> Void) {
        let defaults = UserDefaults.standard
        let saved = defaults.volatileDomain(forName: UserDefaults.argumentDomain)
        var english = saved
        english["AppleLanguages"] = ["en"]
        defaults.removeVolatileDomain(forName: UserDefaults.argumentDomain)
        defaults.setVolatileDomain(english, forName: UserDefaults.argumentDomain)
        defer {
            defaults.removeVolatileDomain(forName: UserDefaults.argumentDomain)
            defaults.setVolatileDomain(saved, forName: UserDefaults.argumentDomain)
        }
        XCTAssertEqual(Locale.preferredLanguages.first, "en")
        body()
    }
}
