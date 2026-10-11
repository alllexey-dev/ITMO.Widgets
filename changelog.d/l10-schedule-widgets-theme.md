# Widgets

- New switch «Виджеты в цвет темы» in the settings root's «Виджеты» group,
  off by default (`widgets_follow_app_theme`): on, both schedule widgets and
  the QR widget take the colours of «Оформление» (colour, palette style and
  contrast; «Чёрный фон» stays out), light or dark as the system is, on
  Android and iOS; their layouts and the lesson type colours stay. Changing
  the switch or the appearance redraws placed widgets and the settings
  previews at once.
- The QR widget's «Динамические цвета» keeps precedence: off, the code stays
  black on white whatever the new switch says.
- iOS: qr-pass-v1.json and schedule-timeline-v1.json gain an optional
  `palette` (additive, still version 1).
