# Settings storage

- A setting changed while a screen or service starts watching it is no
  longer lost until restart: `DataStorePreferences.observe` takes its first
  value from an identity `updateData`, which waits for writes in flight,
  because androidx.datastore 1.2.1 can drop such a write for the new
  collector (for example the social opt-in follower when the demo session
  starts).
