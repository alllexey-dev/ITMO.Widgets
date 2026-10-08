# Schedule widgets

- The day-list widget updates its rows together with the widget, so it no
  longer shows an older list after a refresh.
- On Android 12 and newer both schedule widgets follow the launcher's corner
  radius.
- The schedule widget snapshot moved from Gson to kotlinx JSON with a
  `formatVersion` marker; 2.2 snapshots keep showing until the next refresh.
- The widget selector also produces a versioned timeline (JSON version 1,
  `ScheduleWidgetTimelineJson`) that the iOS schedule widgets read.
