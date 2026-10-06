# Account

- Sign-out also forgets the sport points and attempts, a failed load
  included, so the next account or the demo loads «Мой спорт» again instead of
  showing the previous session's data or «Сессия истекла — войдите снова.»
  (`SportDataRepositoryImpl.clearSessionData`).
