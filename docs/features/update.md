# Update offer

`feature/update` compares the installed version with Backend's
`GET /api/app/version-info` (`minVersion`, `latestVersion`, `note`) once per
process after the session reports a signed-in user. Like every Backend call it
is gated on the opt-in. A failed check offers nothing; the prompt is advisory and
never opens a screen to show an error.

Versions are ordered by `AppVersionName`, so `2.10` follows `2.9` and a
`-SNAPSHOT` build still recognises its own release. `PendingAppUpdate` applies
the policy: at most one offer a day, none for a version the user skipped, and no
limit once the installed build is below `minVersion`, in which case
`Пропустить версию` disappears. The offer records when it was shown, so leaving
by Back postpones it like `Напомнить позже`. `AppUpdateFragment` renders the
check result passed as arguments and has no loading state.

`R.string.app_version` is generated from `versionName`, so the settings display
and version-dependent storage never drift from the APK metadata.
