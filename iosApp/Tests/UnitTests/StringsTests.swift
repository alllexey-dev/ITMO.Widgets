@testable import ITMOWidgets
import Shared
import UIKit
import XCTest

/// The catalog on iOS (L18 IO-20, ADR 0028): Russian text and Russian plural rules on an English simulator from the
/// string tables (SwiftUI, extensions) and from Compose Multiplatform, and every icon of the registry.
///
/// The app process runs `IosStrings.installAppLocale()` in `App.init`; checks of the native path put English back
/// first through `withEnglishLanguages`, as an extension process without Kotlin sees the device.
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
            XCTAssertEqual(AppStrings.plural("sport_capacity_free_label", count: 5), "Свободных мест")
            XCTAssertEqual(AppStrings.plural("sport_capacity_free_label", count: 21), "Свободное место")
            XCTAssertEqual(String(localized: .StringsSport.sportCapacityFreeLabel(count: 2)), "Свободных места")
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
        XCTAssertEqual(AppStrings.string("ios_control_qr_description"), "Открывает QR-пропуск")
    }

    func testArgumentsTakeStringsAndKotlinNumbers() throws {
        let text = try XCTUnwrap(IosStrings.shared.coreString(key: "marks_subjects_more", arguments: ["Физика", 2]))

        XCTAssertEqual(text.resolved, "Физика и ещё\u{00A0}2")
    }

    func testNestedUiTextResolvesFirstLikeKotlin() async throws {
        let count = try XCTUnwrap(IosStrings.shared.corePlural(key: "schedule_lesson_count", count: 21))
        let text = try XCTUnwrap(IosStrings.shared.coreString(key: "schedule_change_added", arguments: [count]))

        XCTAssertEqual(text.resolved, "Добавлена: 21 пара")
        let kotlin = try await IosStrings.shared.resolve(text: text)
        XCTAssertEqual(kotlin, text.resolved)
    }

    func testComposeSuspendResolverUsesRussianPluralRules() async throws {
        for (count, expected) in lessonCounts {
            let text = try XCTUnwrap(IosStrings.shared.corePlural(key: "schedule_lesson_count", count: Int32(count)))
            let resolved = try await IosStrings.shared.resolve(text: text)
            XCTAssertEqual(resolved, expected)
        }
    }

    func testCompositionUsesRussianPluralRules() async throws {
        let text = try XCTUnwrap(IosStrings.shared.corePlural(key: "schedule_lesson_count", count: 5))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let resolved = expectation(description: "composition resolved the plural")
        resolved.assertForOverFulfill = false
        var values: [String] = []
        let window = UIWindow(windowScene: scene)
        window.rootViewController = IosStrings.shared.compositionProbe(text: text) { value in
            values.append(value)
            if !value.isEmpty { resolved.fulfill() }
        }
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        await fulfillment(of: [resolved], timeout: 10)

        XCTAssertEqual(values.last, "5 пар")
    }

    func testEverySharedIconHasASymbol() {
        for icon in AppIcon.entries {
            XCTAssertNotNil(UIImage(systemName: icon.symbol.systemName), icon.id)
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
