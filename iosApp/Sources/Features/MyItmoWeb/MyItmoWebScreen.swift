import Shared
import SwiftUI
import WebKit

/// Android's `MyItmoWebFragment` (IO-08b): `https://my.itmo.ru/` in a `WKWebView` (`MyItmoWebBrowser`), pushed on the
/// tab's stack under the native bar with `my_itmo_web_title`, `my_itmo_web_reload` and the `my_itmo_web_more` menu
/// with `my_itmo_web_external`. The system back button replaces Android's close; the edge swipe inside the page goes
/// back in its history. The demo session never opens it (`ShellGate`).
struct MyItmoWebScreen: View {
    @State private var browser = MyItmoWebBrowser()
    @Environment(\.openURL) private var openURL

    var body: some View {
        MyItmoWebContent(load: browser.load, progress: browser.progress, retry: browser.reload) {
            MyItmoWebView(browser: browser)
        }
        .navigationTitle(Text(verbatim: AppStrings.string("my_itmo_web_title")))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button(action: browser.reload) {
                    AppSymbol.refresh.image
                }
                .accessibilityLabel(Text(verbatim: AppStrings.string("my_itmo_web_reload")))
                .accessibilityIdentifier("myItmoWeb.reload")
                Menu {
                    Button {
                        if let home = URL(string: MyItmoWebPolicy.shared.HOME_URL) { openURL(home) }
                    } label: {
                        Label {
                            Text(verbatim: AppStrings.string("my_itmo_web_external"))
                        } icon: {
                            AppSymbol.openInNew.image
                        }
                    }
                    .accessibilityIdentifier("myItmoWeb.external")
                } label: {
                    AppSymbol.moreVert.image
                }
                .accessibilityLabel(Text(verbatim: AppStrings.string("my_itmo_web_more")))
                .accessibilityIdentifier("myItmoWeb.more")
            }
        }
        .onAppear {
            browser.openExternally = { url in openURL(url) }
            browser.start()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("myItmoWeb.screen")
    }
}

/// The page around its browser for one `MyItmoWebLoad`, without WebKit (snapshot tests pass a stand-in). The
/// progress line keeps its 4 pt while hidden, so the page never jumps; the error covers the browser, which then
/// takes no touches and is hidden from VoiceOver.
struct MyItmoWebContent<Browser: View>: View {
    let load: MyItmoWebLoad
    var progress: Double = 0
    let retry: () -> Void
    @ViewBuilder let browser: Browser

    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: min(max(progress, 0), 1))
                .progressViewStyle(.linear)
                .itmoTint()
                .frame(height: Self.lineHeight)
                .opacity(load == .loading ? 1 : 0)
                .accessibilityHidden(load != .loading)
                .accessibilityIdentifier("myItmoWeb.loading")
            ZStack {
                browser
                    .opacity(load == .failed ? 0 : 1)
                    .allowsHitTesting(load != .failed)
                    .accessibilityHidden(load == .failed)
                if load == .failed {
                    ItmoErrorView(
                        title: AppStrings.string("my_itmo_web_error_title"),
                        description: AppStrings.string("my_itmo_web_error_description"),
                        retry: retry
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
                    .accessibilityIdentifier("myItmoWeb.error")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color(uiColor: .systemBackground))
    }

    /// Android's `trackThickness`.
    private static var lineHeight: CGFloat { 4 }
}

/// The browser's `WKWebView` in SwiftUI; the browser owns it for the screen's lifetime.
private struct MyItmoWebView: UIViewRepresentable {
    let browser: MyItmoWebBrowser

    func makeUIView(context: Context) -> WKWebView {
        browser.webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    static func dismantleUIView(_ webView: WKWebView, coordinator: ()) {
        webView.stopLoading()
    }
}
