# Navigation

- One navigation: `MainActivity` hosts only the Navigation 3 shell
  (`ShellHost`); the Fragment shell (`MainNavigationCoordinator`,
  `AppOverlayHostFragment`, `activity_main.xml`, `bottom_nav.xml`,
  `MainActivityIntentRouting`, `MainRouteQueue`) and its debug-only
  `shell_mode` override are gone. Entry intents and App Links go through
  `EntryRouteParser` and `RouteQueue` in `:shared:core`, and a shared sport
  lesson reaches the sport tab as a tab request kept with the back stack.
- No Fragment host is left: the social, settings, links, reviews, account and
  debug hosts, the nav graphs and `AppNavigator` are gone, and every screen,
  sheet and dialog is a Navigation 3 shell entry.
- Sheets close with the predictive back gesture like overlay screens.
  Nothing else changes for the user.
