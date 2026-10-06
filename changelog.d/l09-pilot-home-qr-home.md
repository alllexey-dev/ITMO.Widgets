# Home

- The home feed is Compose in `:shared:feature-home`, hosted by the same
  `HomeFragment`; its `HomeViewModel` comes from Koin and the cards, the
  pull-to-refresh, the empty state and the two buttons look as in 2.2.
- Feed times and dates are formatted once in the academic time zone
  (`HomeCardFormatter`); the screen parses no dates.
- Friend requests on the home feed use the shared `UserRow` and `Avatar`.
