# iOS notification taps

- On iOS a tap on a local notification opens the screen it is about, as on
  Android, whether the app was running or not: «Расписание изменилось» the
  found changes, «Новые оценки» the recordbook or the one subject's page,
  «Войдите в БАРС» the recordbook with the BARS sign-in. The notification
  carries its destination in `userInfo` (`NotificationTapRoutes.userInfoOf`);
  the tap handler, shared with pushes, reads it with `EntryRouteParser`.
