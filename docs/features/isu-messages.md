# ISU messages

`Сообщения ИСУ` shows the user's own ISU inbox (app 2422 of isu.ifmo.ru): course announcements from teachers,
messages from the dean's office and university centres. Read-only and on the device: nothing is sent, marked,
deleted or passed to Backend or analytics. The code is the `:shared:feature-messages` module.

Status: a design prototype. The list format was checked read-only in a browser on 2026-10-26; the login and the
attachment download were not run on a device yet.

## Screen

`AppRoutes.IsuMessages`, a screen pushed from Профиль -> `Ссылки` -> `Сообщения ИСУ`, over
`IsuMessagesViewModel` (Android `ui/IsuMessagesScreen.kt` in `androidMain`, iOS
`iosApp/Sources/Features/Messages/IsuMessagesScreen.swift`).

- The header says `N непрочитанных` from ISU's own counter of the inbox (`unrd_cntr`).
- `Входящие` / `Архив`: the inbox holds the current calendar year, the archive the years before. Each folder loads
  on first view and keeps its pages until a refresh (pull to refresh, `Повторить`).
- A row: the sender's initials, the sender, a paperclip when there are files, the date (the time today, `21 окт.`
  this year, `2 июля 2025` before), the subject and two lines of the text; an unread message is bold with a dot.
- Ten messages a page; `Показать ещё` asks for the next one. A page with fewer than ten, or with nothing new, ends
  the list.
- A tap opens the message over the list (Android: in the same route, Back closes it; iOS: pushed on the stack):
  sender and date, the body as selectable text, `Вложения`. The body comes from the list already loaded, so opening
  it sends nothing to ISU and ISU keeps it unread; an unread message says so under the body.
- Links in the body open outside the app (`PlatformActions.openLink` on Android, Safari on iOS). An attachment is
  downloaded and opened in the system viewer: Android `ACTION_VIEW` on a `content://` address of the app's
  `FileProvider` (`cache/isu-files`), iOS Quick Look on `tmp/isu-files`. Only the last opened file is kept.
- States: loading; empty (`Сообщений нет`, `В архиве пусто`); `ИСУ не узнаёт вход` when ITMO.ID no longer signs in
  (sign out and in again); `Сообщения не загрузились` with the error's text. A failed refresh keeps the list and
  shows a snackbar.

Why Профиль: the screen needs one ISU login (several redirects) per session, which Сегодня should not pay on every
open, and the row needs no new contract between features. An unread card on Сегодня is a later step once the poll
runs in the background check.

## Data

ISU is read through core's on-device `IsuWebClient` ([ADR 0037](../decisions/0037-on-device-isu-client.md)): it signs
in with the phone's ITMO.ID cookies, keeps ISU's cookies in one in-memory `IsuSession` and refuses any other host.
`IsuMessagesRepositoryImpl` adds the messages app (2422) on top of it.

| Step | Request | Read |
|---|---|---|
| Sign-in | `IsuWebClient.signIn()`: the portal `f?p=2143:1` through ITMO.ID's redirect | the APEX session; none means ITMO.ID wants the user |
| Inbox shell | `IsuSession.page(2422, "4")` once per session, as the browser opens it | the hidden `USER_ID`: when present it must be the token's ISU number, else the session is refused |
| List | `IsuSession.process(2422, 4 or 8, "MAIN_FN", ...)`: `POST wwv_flow.show` with `x01=<own ISU>`, `x02=<page>`, and `x03=1` for the archive (page 8) | `unrd_cntr`, `incoming[]`: `msgid`, `sender_name`, `subject`, `text` (HTML), `date` (`21.10.2026`), `sender_date` (`21 окт. 15:51`), `is_read` (`Y`/`N`), `files[]` (`hash_id`, `name`, `type`) |
| Attachment | `IsuSession.download("f?p=2422:0::DWNLD_F_GEN:NO::FILE:<hash_id>", 40 MiB)` | the bytes; a page instead means the session was lost |

- **Own account only.** `x01` is always the ISU number of the ITMO.ID token (`CurrentUserProvider`), never a value
  from a screen or a page. The request that opens one message (page 3) is never sent: it most likely marks the
  message read in ISU.
- **Session.** One `IsuSession` is kept in memory for the token's ISU number; a session ISU lost (a page instead of
  the answer) is signed in again once; sign-out drops it with the saved attachment (`SessionDataCleaner`
  `isu-messages`).
- **Format.** `IsuMessagesJson` reads fields by name and tolerates numbers as strings; an item without `msgid` is
  skipped, an answer without `incoming` is a failure. Only a 32-hex `hash_id` becomes an attachment.
- **Body.** `MessageHtml` turns the HTML into `MessageBody` blocks by a whitelist (ksoup): paragraphs, line breaks,
  bold, italic, underline, list items and `http(s)` links, bare `http(s)` addresses as links. Scripts, styles,
  images, forms and other link schemes are dropped with no trace but their text. No HTML is rendered anywhere.
- **Demo.** The demo session reads `DemoIsuMessages` (fictional senders and links) and never reaches ISU;
  attachments answer `error_demo_unavailable`.

## Open questions

- The ISU `MAIN_FN` takes the user id from the client (`x01`); whether ISU checks it against the session is not
  known (research notes of 2026-10-26). The app never sends another number either way.
- Not done: push or a background check for new messages, subject-page links, itmo-mcp tools, search, sent messages.
