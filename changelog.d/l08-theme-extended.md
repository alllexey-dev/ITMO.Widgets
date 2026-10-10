# Design system

- New settings page «Оформление» (from «Приложение» on the settings root) with
  a live sample: «Цвет оформления» gains «Свой цвет» (a HEX field and hue,
  saturation and brightness sliders on Android, the system colour well on
  iOS), plus «Стиль палитры» (seven Material styles), «Контраст» (Обычный,
  Средний, Высокий) and «Чёрный фон» for the dark theme. New keys
  `app_accent_custom`, `app_theme_style`, `app_theme_contrast`,
  `app_dark_black`; the defaults keep today's look.
- `resolveColorScheme` turns the stored appearance into the light or dark
  scheme for every Compose screen and, next, the widgets.
