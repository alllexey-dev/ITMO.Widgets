# Dependency injection

- Koin is the app's only dependency-injection graph: the Application, the
  activities, the workers, the widgets, the tile, the FCM service and every
  Android binding of `di/` resolve through it (`KoinStarter`,
  `KoinModules`). Hilt, Dagger and KSP are gone from the build, and Konsist
  bans `dagger.`, `javax.inject` and the Hilt annotations in every source
  set. Instrumented tests read the real graph through `TestGraph`. Nothing
  changes for the user.
