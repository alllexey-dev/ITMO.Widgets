# Home

- The home feed is Compose in `:shared:feature-home`, hosted by the same
  `HomeFragment`; its `HomeViewModel` comes from Koin and the cards, the
  pull-to-refresh, the empty state and the two buttons look as in 2.2.
- Each home card is drawn by the feature that produces it: schedule and
  schedule changes by `:shared:feature-schedule`, new marks by
  `:shared:feature-recordbook`, sport by `:shared:feature-sport`, friend
  requests by `:shared:feature-social`, the hints by home. A feature
  registers one `HomeCardRenderer` in its Koin module; the feed draws each
  card with the renderer of its `HomeCardKind`.
- Feed times and dates are formatted in the academic time zone by each
  card's renderer; the feed screen parses no dates.
- The card frame, header, row badge and close button are the kit's
  `FeedCard` parts (`designsystem/components/cards/FeedCard.kt`).
- Friend requests on the home feed use the shared `UserRow` and `Avatar`.
