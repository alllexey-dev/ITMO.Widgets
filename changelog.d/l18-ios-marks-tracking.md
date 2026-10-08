# iOS mark tracking

- The iOS app checks My ITMO, BARS and the connected sheets for new and
  changed marks every three hours in the background refresh, after the
  schedule changes, and on every return to the app; a new mark shows
  «Новые оценки» with the subject names only, and the home feed gets the
  «Новые оценки» card that opens «Зачётка».
- BARS is renewed in the background through the ITMO.ID cookies only, as on
  Android; without them, or when ITMO.ID wants a sign-in, the app asks once
  with «Войдите в БАРС». Marks and the reminder found at night arrive at
  06:00.
- Settings show the «Зачётка» page with «Оценки My ITMO», «Оценки БАРС» and
  «Оценки из таблиц».
