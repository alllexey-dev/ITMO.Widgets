// The Compose side of the string catalog, Android only (ADR 0035): a CMP copy of every module's composeResources
// strings, so one generated Res maps every catalog key to its StringResource, and the UiText resolvers of Compose and
// suspend code over it. The catalog files stay in their modules, which compile no Res of their own.
plugins {
    id("itmowidgets.cmp.kit")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // UiText is in the resolvers' signatures, StringResource in the public Res that Compose code reads.
            api(project(":shared:core"))
            api(libs.compose.components.resources)
        }
        // The core test fixtures compile here for SharedTextsRussianTest.
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

val catalogCopies = tasks.register<Sync>("syncStringCatalogs") {
    description = "Copies the composeResources string files of every shared module into one CMP resource directory."
    from(isolated.rootProject.projectDirectory.dir("shared")) {
        include("*/src/commonMain/composeResources/values*/strings*.xml")
        // values*/strings_<file>.xml: file names are unique across the catalog (they name the Apple tables).
        eachFile { relativePath = RelativePath(true, *relativePath.segments.takeLast(2).toTypedArray()) }
    }
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.FAIL
    into(layout.buildDirectory.dir("generated/itmowidgets/catalog"))
}

compose.resources {
    customDirectory("commonMain", catalogCopies.map { layout.projectDirectory.dir(it.destinationDir.path) })
}
