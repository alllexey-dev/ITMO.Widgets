# iOS account

- The iOS app opens on the sign-in screen of Android: the logo, «ITMO.Widgets»,
  what the app does, «Войти через ITMO.ID» (the ITMO.ID page above it) and
  «Другой способ входа» for a refresh token, over the shared `AuthViewModel`;
  the ITMO.ID page runs on the shared `InteractiveLoginViewModel`.
- Five taps on the logo open the demo session on iOS too, with a haptic and
  a VoiceOver announcement of «Демо-режим».
- A new account on iOS walks the first-run flow of Android over the shared
  `OnboardingViewModel`: each widget step explains how to add the widget («Как
  добавить виджет»; iOS lets no app place one) beside its appearance rows, then
  the services opt-in and the notification question through iOS's own dialog.
- Signing in, starting the demo or signing out on iOS no longer deletes the
  App Group container's own record, so the QR widget sees the session and the
  pass the app writes instead of «Войдите в приложение».
