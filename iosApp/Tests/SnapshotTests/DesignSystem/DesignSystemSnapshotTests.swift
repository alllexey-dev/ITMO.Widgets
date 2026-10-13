import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The SwiftUI kit in all four appearances: form rows, the loading, empty and error states, the progress
/// button and the demo banner. The empty and error states are the system's unavailable view with a glass button,
/// drawn in a window. Texts are synthetic; the kit's own defaults (retry, demo banner) come from the catalog.
@MainActor
final class DesignSystemSnapshotTests: XCTestCase {
    /// Tall enough for the section at AX1; a `Form` scrolls, so it has no height of its own.
    private let formHeight: CGFloat = 640

    func testFormToggleAndValueRows() {
        let form = Form {
            ItmoFormSection(header: "Уведомления", footer: "Приходят, даже когда приложение закрыто.") {
                ItmoToggleRow(
                    title: "Изменения в расписании",
                    subtitle: "Новые, отменённые и перенесённые пары",
                    symbol: .notification,
                    isOn: .constant(true)
                )
                ItmoToggleRow(title: "Новые баллы", isOn: .constant(false))
                ItmoValueRow(title: "Тема", value: "Как в системе", symbol: .settings)
            }
        }
        .scrollDisabled(true)
        assertAppearances(of: form, named: "form", height: formHeight)
    }

    func testFormLongAndActionRows() {
        let form = Form {
            ItmoFormSection {
                ItmoValueRow(title: SnapshotFixtures.longSubjectName, value: "92", subtitle: SnapshotFixtures.longPersonName)
                ItmoActionRow(title: "Обновить", symbol: .refresh) {}
                ItmoActionRow(title: "Выйти из аккаунта", symbol: .logout, role: .destructive) {}
            }
        }
        .scrollDisabled(true)
        assertAppearances(of: form, named: "form", height: formHeight)
    }

    func testLoading() {
        assertAppearances(of: ItmoLoadingView(title: "Загружаем настройки"), named: "loading")
    }

    func testLoadingWithoutTitle() {
        assertAppearances(of: ItmoLoadingView(), named: "spinner", appearances: [.light, .dark])
    }

    func testEmpty() {
        let view = ItmoEmptyView(
            symbol: .eventNote,
            title: "Пар нет",
            description: "На этот период расписание пустое."
        )
        assertScreenAppearances(of: view, named: "empty", height: nil)
    }

    func testEmptyLongTexts() {
        let view = ItmoEmptyView(
            symbol: .school,
            title: SnapshotFixtures.longSubjectName,
            description: SnapshotFixtures.longPersonName
        )
        assertScreenAppearances(of: view, named: "long", height: nil)
    }

    func testError() {
        let view = ItmoErrorView(title: "Не удалось загрузить", description: "Проверьте подключение к интернету.") {}
        assertScreenAppearances(of: view, named: "error", height: nil)
    }

    func testProgressButton() {
        let buttons = VStack(spacing: ItmoSpacing.group) {
            ItmoProgressButton(title: "Войти через ITMO.ID", symbol: .login) {}
            ItmoProgressButton(title: "Войти через ITMO.ID", symbol: .login, isInProgress: true) {}
            ItmoProgressButton(title: SnapshotFixtures.longSubjectName) {}
        }
        .padding(ItmoSpacing.screenMargin)
        assertAppearances(of: buttons, named: "button")
    }

    func testDemoBanner() {
        assertAppearances(of: ItmoDemoBanner {}, named: "banner")
    }

    func testDemoBannerFullWidth() {
        assertAppearances(of: ItmoDemoBanner {}, named: "wide", width: 440)
    }
}
