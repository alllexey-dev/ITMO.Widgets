# iOS background refresh

- The iOS app refreshes the widget timelines and checks the schedule for
  changes when iOS wakes it for a background refresh, at launch and on every
  return to the app (`BackgroundRunner`, task
  `dev.alllexey.itmowidgets.refresh`); each check keeps Android's period, so
  My ITMO gets no more requests than from an Android phone.
- Schedule change notifications on iOS use Android's texts («Расписание
  изменилось: 1 пара»); a change found at night is delivered at 06:00.
