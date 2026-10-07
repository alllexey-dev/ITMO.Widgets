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
- «Вход на сайт» works on iOS over the shared `WebLoginViewModel`: the QR is
  read by the system scanner (VisionKit, after iOS asks for the camera) or the
  code typed from the screen, then the browser card and «Войти».
- My ITMO opens on iOS in the app's browser on the sign-in's website data:
  only `my.itmo.ru` and `id.itmo.ru` stay inside, a tapped link to another
  site opens in Safari, anything else shows «Не удалось открыть My ITMO».
- The iOS app offers a newer iOS release from Backend («Вышла новая версия»)
  with «Скачать» to its App Store page; with no App Store ID in the build
  (until the App Store record exists) it never checks or offers.
