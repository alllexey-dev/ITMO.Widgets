# Navigation

- The bottom tabs also switch by a horizontal swipe on a tab root in the
  Compose shell: one tab per swipe in bar order, no wrap-around, committed
  past `TabSwipeDefaults.commitFraction` or by a fling; the bar marks the
  target tab with one threshold haptic. Inner horizontal content scrolls
  first, the swipe is off while an overlay, sheet or dialog is open and under
  TalkBack, and routes, taps and Back still land on an `AppTab` without
  sliding through the tabs between (`TabPager`, `TabPages`).
