# Strings and locale

- The app speaks Russian on a phone in any language: plurals follow Russian
  rules («5 пар», «Ещё 2 балла» instead of «5 пары», «Ещё 2 баллов») and
  library texts such as «Перейти вверх» are Russian. `AppLocale` sets the
  per-app locale `ru` at start (`LocaleManager` on API 33+, AppCompat below),
  `res/xml/locales_config.xml` offers only Russian, and notifications,
  channels, widgets and the QR tile build their text through
  `Context.withAppLocale()`, because the application context keeps the system
  language below API 33 and returns to it on API 33+ once the last activity
  closes. Builds keep only `ru` library resources and an app bundle has no
  language splits.
