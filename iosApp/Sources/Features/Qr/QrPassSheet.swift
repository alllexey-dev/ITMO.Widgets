import Shared
import SwiftUI
import UIKit

/// The pass sheet (DECISIONS.md, the pass) over the shared `QrPassViewModel`: who the pass belongs to, the code, and
/// when it was loaded with the refresh button; no validity or countdown. The today accessory, the widgets, the
/// Control, the shortcuts and `itmowidgets://route/qr_pass` all open it. The screen is at full brightness while the
/// sheet is on screen and the app is active, so a turnstile reads the code (Android's pass keeps the user's
/// brightness).
struct QrPassSheet: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = ObservableViewModel(QrPassViewModel.self, state: \.uiState)
    @State private var brightness = ScreenBrightness()
    @State private var message: SettingsMessage?
    @State private var isVisible = false

    var body: some View {
        ItmoSheet(title: AppStrings.string("qr_pass_sheet_title"), height: .content) {
            QrPassContent(state: model.state, refresh: { model.viewModel.refresh(mode: .force) })
        }
        .overlay(alignment: .bottom) {
            if let message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                        if self.message?.id == message.id { self.message = nil }
                    }
            }
        }
        .animation(.default, value: message?.id)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            guard case let .refreshFailed(failed) = onEnum(of: event) else { return }
            let text = AppErrorTextsKt.toUiText(failed.error).resolved
            message = SettingsMessage(text: text)
            AccessibilityNotification.Announcement(text).post()
        }
        .accessibilityIdentifier("qr.pass")
        .onAppear {
            isVisible = true
            model.viewModel.start()
            if scenePhase == .active { brightness.raise() }
        }
        .onDisappear {
            isVisible = false
            model.viewModel.stop()
            brightness.restore()
        }
        .onChange(of: scenePhase) { _, phase in
            guard isVisible else { return }
            // Requests run only while the sheet is on screen; the background stops them.
            switch phase {
            case .active:
                model.viewModel.start()
                brightness.raise()
            case .background:
                model.viewModel.stop()
                brightness.restore()
            default:
                brightness.restore()
            }
        }
    }
}

/// The sheet's content without the ViewModel: the holder, the code or its state, and the line under the code.
struct QrPassContent: View {
    let state: QrPassUiState
    var refresh: () -> Void = {}

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            holder
            area
            statusLine
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var holder: some View {
        if let holder = state.holder {
            VStack(spacing: 2) {
                if let name = holder.name {
                    Text(verbatim: name).font(.itmo(.bodyLarge, emphasized: true))
                }
                if let details = holder.details {
                    Text(verbatim: details.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
            }
            .multilineTextAlignment(.center)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("qr_pass_holder")
        }
    }

    @ViewBuilder
    private var area: some View {
        if let code = state.code, let matrix = QrPassMatrix.rows(hex: code.hex) {
            VStack(spacing: ItmoSpacing.compact) {
                QrPassCodeView(matrix: matrix, dynamicColors: code.useDynamicColors, dark: colorScheme == .dark)
                    .opacity(isRefreshing ? 0.25 : 1)
                    .overlay { if isRefreshing { ProgressView().controlSize(.large) } }
                Text(verbatim: AppStrings.string("qr_pass_description"))
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        } else if state.code != nil {
            ItmoEmptyView(
                symbol: .qrCode,
                title: AppStrings.string("common_load_error_title"),
                description: AppStrings.string("qr_pass_empty_description")
            )
        } else if let failed = state.status as? QrPassStatusFailed {
            ItmoErrorView(
                title: AppStrings.string("common_load_error_title"),
                description: AppErrorTextsKt.toUiText(failed.error).resolved,
                retry: refresh
            )
        } else if state.status is QrPassStatusEmpty {
            ItmoEmptyView(
                symbol: .qrCode,
                title: AppStrings.string("qr_pass_empty_title"),
                description: AppStrings.string("qr_pass_empty_description"),
                action: (AppStrings.string("qr_pass_refresh"), refresh)
            )
        } else {
            ItmoLoadingView()
                .accessibilityIdentifier("qr_pass_loading")
        }
    }

    /// When the code was loaded, with the refresh button at the end; only under a code.
    @ViewBuilder
    private var statusLine: some View {
        if let line = state.status.line {
            let offline = state.status is QrPassStatusOffline
            HStack(spacing: ItmoSpacing.content) {
                Group {
                    if isRefreshing {
                        ProgressView()
                    } else {
                        (offline ? AppSymbol.cloudOff : AppSymbol.history).image
                    }
                }
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
                Text(verbatim: line.resolved)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("qr_pass_line")
                Button(action: refresh) {
                    Label {
                        Text(verbatim: AppStrings.string(offline ? "common_retry" : "qr_pass_refresh"))
                    } icon: {
                        AppSymbol.refresh.image
                    }
                    .font(.itmo(.bodyMedium, emphasized: true))
                }
                .disabled(isRefreshing)
                .itmoTint()
                .accessibilityIdentifier("qr_pass_refresh")
            }
        }
    }

    private var isRefreshing: Bool {
        state.status is QrPassStatusRefreshing
    }
}

/// The code: the shared generator's modules, drawn as the QR widget draws them, on a rounded plate. Black on white,
/// or the app's scheme with the user's QR colour setting (`QrWidgetTile.pass`).
struct QrPassCodeView: View {
    let matrix: [[Bool]]
    let dynamicColors: Bool
    let dark: Bool

    /// The code's side at the default text size; it grows with the text up to `maxSide`.
    @ScaledMetric(relativeTo: .body) private var side: CGFloat = 220
    private let maxSide: CGFloat = 280

    var body: some View {
        let tile = QrWidgetTile.pass(dynamicColors: dynamicColors, dark: dark)
        let length = min(side, maxSide)
        QrModulesShape(matrix: matrix)
            .fill(tile.foreground)
            .padding(length / 14)
            .frame(width: length, height: length)
            .background(tile.background, in: .rect(cornerRadius: ItmoKit.cardCorner))
            .accessibilityElement()
            .accessibilityLabel(Text(verbatim: AppStrings.string("qr_pass_image_description")))
            .accessibilityAddTraits(.isImage)
            .accessibilityIdentifier("qr_pass_image")
    }
}

/// The modules of a pass from the shared `QrCodeGenerator`, rows from the top; nil for a code too long for version 1.
enum QrPassMatrix {
    static func rows(hex: String) -> [[Bool]]? {
        guard hex.unicodeScalars.count <= Int(QrCode.companion.MAX_BYTES) else { return nil }
        let generator = QrCodeGenerator()
        // The generator's outer index is the column; SwiftUI draws rows.
        let columns = generator.toBooleans(qrCode: generator.generate(hex: hex))
        return (0..<columns.count).map { y in
            (0..<columns.count).map { x in columns[x][y].boolValue }
        }
    }
}

/// Full brightness for the pass and the user's own level back afterwards. The level is saved once per raise, so a
/// second raise never saves 1.0 as the user's level.
@MainActor
final class ScreenBrightness {
    private var saved: CGFloat?

    func raise() {
        guard saved == nil, let screen = Self.activeScreen() else { return }
        saved = screen.brightness
        screen.brightness = 1.0
    }

    func restore() {
        guard let level = saved else { return }
        saved = nil
        Self.activeScreen()?.brightness = level
    }

    /// The screen of the foreground scene; `UIScreen.main` is deprecated.
    private static func activeScreen() -> UIScreen? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return (scenes.first { $0.activationState == .foregroundActive } ?? scenes.first)?.screen
    }
}
