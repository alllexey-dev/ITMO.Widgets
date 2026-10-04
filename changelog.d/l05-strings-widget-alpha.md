# Schedule widgets

- The schedule widgets show lessons again on Android 8-11: a finished lesson
  is dimmed through its text colours and the type indicator's image alpha
  there, because `View.setAlpha` is a RemoteViews method only from Android 12,
  where the widgets keep fading the whole lesson as before.
