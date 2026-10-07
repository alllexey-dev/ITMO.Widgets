# Sign-in

- The sign-in screen and the ITMO.ID page are Compose screens of
  `:shared:feature-account` (`AuthScreen`, `AuthRoute`, `LoginScreen`); they
  look and behave as before. `AuthFragment` and `LoginActivity` keep their
  names and host them; the ITMO.ID WebView sits in the page's slot inside a
  `SwipeRefreshLayout`, so pull to reload still works.
- The «Вход по Refresh token» dialog is the kit `ConfirmDialog`; the typed
  token lives only while the dialog is open and is never saved.
- The demo entry times the five logo taps on a monotonic `TimeSource`
  instead of `SystemClock.uptimeMillis()`; `AuthViewModel.onLogoTap()` takes
  no argument. The logo stays without an accessibility focus stop.
- The logo moved to the shared module's `composeResources/drawable`, and the
  `strings_auth.xml` Android export is retired.
