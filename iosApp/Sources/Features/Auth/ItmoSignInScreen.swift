import SwiftUI

/// The signed-out gate on the shared session (IO-07a): the ITMO.ID page in a `WKWebView`, where the user types the
/// credentials, under the sign-in title. A failed page or sign-in replaces the browser with an error and a retry;
/// handing the tokens over shows a progress cover. IO-07b puts the auth screen with the demo entry in front of it.
struct ItmoSignInScreen: View {
    @State private var model: ItmoSignInModel

    init(gateway: SessionGateway) {
        _model = State(initialValue: ItmoSignInModel(gateway: gateway))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ZStack {
                ItmoSignInWebView(model: model)
                    .opacity(model.showsError ? 0 : 1)
                    .accessibilityHidden(model.showsError)
                if model.showsError {
                    ItmoErrorView(title: AppStrings.string(model.errorTitleKey), retry: model.retry)
                        .frame(maxHeight: .infinity)
                        .background(Color(uiColor: .systemBackground))
                        .accessibilityIdentifier("auth.signIn.error")
                } else if model.isCompleting {
                    ItmoLoadingView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color(uiColor: .systemBackground))
                        .accessibilityIdentifier("auth.signIn.completing")
                }
            }
        }
        .background(Color(uiColor: .systemBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("auth.signIn")
    }

    private var header: some View {
        HStack(spacing: ItmoSpacing.related) {
            Text(verbatim: AppStrings.string("auth_title"))
                .font(.itmo(.headlineSmall))
                .lineLimit(2)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if model.page == .loading, !model.showsError, !model.isCompleting {
                ProgressView()
                    .accessibilityIdentifier("auth.signIn.loading")
            }
        }
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .frame(minHeight: 64)
    }
}
