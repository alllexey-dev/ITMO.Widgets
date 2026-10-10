# Design system

- New setting «Цвет оформления» on the settings root: «Как обои» (Android 12+,
  the default there), «Фирменный» (the default elsewhere) and six presets
  («Бирюзовый», «Зелёный», «Янтарный», «Красный», «Розовый», «Фиолетовый»)
  in a swatch picker that recolours every Compose screen at once, on Android
  and iOS. Stored in `app_accent_color`; widgets keep their colours.
