# R8 rules of the app. Hilt, Koin, WorkManager, Retrofit 2.11, Gson 2.11, OkHttp, Firebase and
# kotlinx.serialization ship their own; @JavascriptInterface methods are kept by proguard-android-optimize.txt.
#
# What is left is class identity for Koin and Gson reflection.

# Class identity. Koin keys every definition by its KClass (`single<T>`, `bind<T>()`, `singleOf(::T)`,
# `viewModel { T(...) }`) and KoinStarter's allowOverride(false) fails the start on a duplicate key. R8 class
# merging folds an interface or abstract class into its only implementer (vertical) and alike classes into one
# (horizontal), which turns two keys into one: 2.3.0-beta.1 died in Application.onCreate() after R8 folded both
# WidgetRefreshRequester and ScheduleWidgetRefreshRequester into WidgetRefreshCoordinator (R8-FIX1). The same
# identity backs the Nav3 keys (AppRoute subtypes registered by KClass in the polymorphic SerializersModule) and
# the ViewModelStore keys (class name).
# A keep rule without allowoptimization stops merging of the class; allowshrinking still drops it when unused,
# allowobfuscation still renames it, and its members stay optimizable. It covers every named non-enum type of
# ours, interfaces and abstract classes included, so a new binding needs no new line. Left mergeable, since none
# is ever a key: the compiler's anonymous classes (names ending in a digit: lambdas, continuations, `object :`
# expressions), file facades (`*Kt`) and Dagger/Hilt's generated classes (`*_*`, `Dagger*`).
# A duplicate key shows only when the shrunk app starts: scripts/ship-check.sh stage 6 launches it.
-keep,allowobfuscation,allowshrinking !enum !**$*0,!**$*1,!**$*2,!**$*3,!**$*4,!**$*5,!**$*6,!**$*7,!**$*8,!**$*9,!**Kt,!**_*,!**.Dagger*,dev.alllexey.**

# Gson reflection: field names are the JSON keys (of the wire and of files written on the
# device by builds without R8), fields are written only by Gson, and enums are read through their constants.
# Each class Gson reads or writes therefore keeps its name, fields, constructors and enum constants.
# Drop a line when its class moves to kotlinx JSON (KM-05a-c) and when Core 1.x and MyItmoApi 1.x leave (KM-10i).

# Generic field types (List<...>, Map<String, ...>) are read from the Signature attribute.
-keepattributes Signature

# Core 1.x: Backend DTOs and the type adapters and factories registered on WidgetsClient.gson.
-keep class dev.alllexey.itmowidgets.core.model.** { *; }
-keep class dev.alllexey.itmowidgets.core.utils.** { *; }

# MyItmoApi 1.x: MyITMO and BARS DTOs and adapters.
-keep class api.myitmo.model.** { *; }
-keep class api.myitmo.adapters.** { *; }
-keep class api.bars.model.** { *; }

# Gson stores of the app, with the shared types they embed.
-keep class dev.alllexey.itmowidgets.feature.schedule.domain.widget.** { *; }
-keep class dev.alllexey.itmowidgets.feature.resources.data.LocalLink { *; }
-keep class dev.alllexey.itmowidgets.feature.resources.data.LocalPin { *; }
-keep class dev.alllexey.itmowidgets.feature.resources.data.CachedLinks { *; }
-keep class dev.alllexey.itmowidgets.feature.resources.data.StoredLinks { *; }
-keep class dev.alllexey.itmowidgets.feature.reviews.data.StoredLevel { *; }
-keep class dev.alllexey.itmowidgets.feature.reviews.data.StoredLevels { *; }
-keep class dev.alllexey.itmowidgets.core.resources.ResourceScope { *; }
-keep class dev.alllexey.itmowidgets.core.settings.LessonStyle { *; }
-keep class dev.alllexey.itmowidgets.core.settings.WidgetTextSize { *; }
-keep class dev.alllexey.itmowidgets.core.network.OffsetDateTimeAdapter { *; }

# Readable stack traces in Play vitals; the mapping file restores the names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
