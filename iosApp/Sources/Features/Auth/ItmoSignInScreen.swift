import Shared
import SwiftUI

/// Android's `LoginActivity`: the ITMO.ID page in a `WKWebView` under a close button and `auth_web_title`, where the
/// user types the credentials. A failed page or sign-in replaces the browser with an error and a retry; handing the
/// tokens over shows a progress cover. `close` runs on the close button and once the sign-in completed.
struct ItmoSignInScreen: View {
    @State private var model: ItmoSignInModel
    let close: () -> Void

    init(model: ItmoSignInModel? = nil, close: @escaping () -> Void) {
        _model = State(initialValue: model ?? ItmoSignInModel())
        self.close = close
    }

    var body: some View {
        NavigationStack {
            content(model.state)
                .navigationTitle(Text(verbatim: AppStrings.string("auth_web_title")))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(action: close) {
                            AppSymbol.close.image
                        }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                        .accessibilityIdentifier("auth.signIn.close")
                    }
                    ToolbarItem(placement: .primaryAction) {
                        if model.state.page == .loading, !model.state.showsError, !model.state.completingLogin {
                            ProgressView()
                                .accessibilityIdentifier("auth.signIn.loading")
                        }
                    }
                }
        }
        .observing(model.login)
        .onEvents(of: model.login, \.events) { event in
            switch onEnum(of: event) {
            case .completed: close()
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("auth.signIn")
    }

    private func content(_ state: InteractiveLoginUiState) -> some View {
        ZStack {
            ItmoSignInWebView(model: model)
                .opacity(state.showsError ? 0 : 1)
                .accessibilityHidden(state.showsError)
            if state.showsError {
                ItmoErrorView(title: model.errorTitle, retry: model.retry)
                    .frame(maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
                    .accessibilityIdentifier("auth.signIn.error")
            } else if state.completingLogin {
                ItmoLoadingView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .systemBackground))
                    .accessibilityIdentifier("auth.signIn.completing")
            }
        }
        .background(Color(uiColor: .systemBackground))
    }
}
