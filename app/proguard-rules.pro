# R8 rules of the app. Hilt, Koin, WorkManager, Retrofit 2.11, Gson 2.11, OkHttp, Firebase and
# kotlinx.serialization ship their own; @JavascriptInterface methods are kept by proguard-android-optimize.txt.
#
# What is left is Gson reflection: field names are the JSON keys (of the wire and of files written on the
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
