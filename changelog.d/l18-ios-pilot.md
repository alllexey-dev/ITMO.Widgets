# iOS

- The iOS app shows the QR pass on the shared screen at full brightness,
  opened from home, the small QR widget, the «QR-пропуск» Control in Control
  Center, on the Lock Screen and the Action button, the App Shortcuts
  «QR-пропуск» and «Сегодня» in Siri and Spotlight, and the quick actions
  «Открыть QR-пропуск» and «Расписание на сегодня» on the app icon.
- The QR widget draws the pass the app last saved, behind a spoiler that
  reveals the code for 30 s, and asks to open the app when the pass expired.
- The iOS QR widget follows «Скрывать QR-код за спойлером» and «Динамические
  цвета»: without the spoiler it shows the code at once, with dynamic colours
  the code and the spoiler take the app's colours, dark on light in both
  themes, and on a tinted home screen the code stays readable. A changed
  switch reaches placed widgets at once (qr-pass-v1.json carries the options).
- `isCausedByNetworkFailure` counts okio's `IOException` as well as
  kotlinx-io's, so an iOS failure Android reports as a network error is no
  longer a generic error (on Kotlin/Native the two are unrelated classes).
