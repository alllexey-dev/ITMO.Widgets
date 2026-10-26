# Wi-Fi

Product behaviour: [Как это работает: Wi-Fi](../how-it-works/wifi.md).

`feature/wifi` (`:shared:feature-account`) shows the university's Wi-Fi account: the network `ITMO-Corp`, the login
and the password from ISU, and a quick connect. The `Wi-Fi` row of Профиль (under `Карта`) opens it as a
content-height sheet, `AppRoutes.Wifi`; the demo session opens it too.

## Network

`ITMO-Corp` is WPA2-Enterprise: EAP-PEAP with MSCHAPv2 inside, no anonymous identity, the login is the user's ISU
number and the password is a separate Wi-Fi password that the user issues in ISU. The RADIUS server presents a Let's
Encrypt certificate for `wifi.it.itmo.ru` that is renewed every ~90 days, so clients check it against the system CA
store and that domain, never a pinned leaf. Constants: `ItmoWifi` (domain). `ITMO-Guest` (phone number, captive
portal) and `eduroam` (guests of other institutions) are not offered.

## Data

- The password is on ISU's «Настройки Wi-Fi» page, `f?p=2156:6`, in the first HTML: `span#P6_USERNAME` (the login),
  `span#P6_PASS_CURRENT > span.wifi-pass` (the active password, hidden only by CSS), and `input#USER_ID` (whose
  cabinet). `IsuWifiPage` parses it; a page of another ISU is refused, a field without a password is `NoPassword`.
  The page's `Получить или изменить пароль Wi-Fi` is never touched.
- ISU is read on the device by `IsuWebClient` (`core/network`, [ADR 0037](../decisions/0037-on-device-isu-client.md)):
  `f?p=2143:1` with the phone's ITMO.ID SSO cookies (`ItmoIdCookies`) through ISU's OIDC redirects, then the page in
  the same APEX session. No SSO cookies (a refresh-token sign-in) or ITMO.ID's form is `SignInRequired`.
- `WifiRepositoryImpl` keeps `"<isu>\n<login>\n<password>"` in `SecureStore` under `wifi_credentials.enc` (a
  Keystore-sealed file in `noBackupFilesDir` on Android, a Keychain item on iOS), bound to the owner's ISU. A read
  password replaces it, `NoPassword` deletes it, a failure keeps it. It is the `SessionDataCleaner` `named("wifi")`.
- The demo answers `DemoWifi` (the demo ISU and a password no network accepts) and never reaches ISU.

## Sheet

`WifiViewModel.start()` shows the stored credentials at once and reads ISU again each time the sheet opens; a failed
read keeps them. Without stored credentials the sheet shows loading, `Пароля Wi-Fi ещё нет`, `ИСУ не открылась`
(both with `Открыть ИСУ`, the page in the browser) or the error with a retry. The password is dots until the eye
button; the copy buttons copy the login or the password (marked sensitive on Android, local-only and expiring in two
minutes on iOS). A note gives the manual settings.

## Quick connect

- **Android 11+.** `Подключиться` starts `Settings.ACTION_WIFI_ADD_NETWORKS` with one `WifiNetworkSuggestion`
  (`addItmoWifiIntent`): PEAP, MSCHAPv2, identity and password, the ISRG roots of `AndroidCAStore` as CA
  certificates and `wifi.it.itmo.ru` as the domain suffix. The system asks to save the network; the result is
  `Сеть ITMO-Corp сохранена`, or a copied password with the hint when it fails.
- **Android 8-10, no ISRG root, or no dialog.** The password is copied and the Wi-Fi settings open with
  `Пароль скопирован: выберите ITMO-Corp и введите логин`.
- **iOS.** No quick connect and no `Подключиться`: `NEHotspotConfiguration` needs the Hotspot Configuration
  capability, which the app does not take because its builds are re-signed by the user (AltStore, SideStore). The
  password is copied and pasted in the Wi-Fi settings with the manual settings from the note
  ([degradations](../ios.md#degradations)).

Not verified on a device on the real network: the server check (system roots plus domain) is inferred from the
certificate a Mac accepted on `ITMO-Corp`.
