// Convention plugins for every module (ADR 0018). Plugin versions come from the root catalog only: the
// conventions' dependencies put AGP, Kotlin, CMP, KSP, Hilt, google-services and Roborazzi on the build
// classpath once, and modules apply them by id without a version.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
// L05 owns :strings from TC-16a on; until then itmowidgets.strings is a no-op.
include(":strings")
