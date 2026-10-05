// The Compose kit: theme, tokens and components with their previews and goldens (recipe kmp-module-build-file).
plugins {
    id("itmowidgets.cmp.ui")
    id("itmowidgets.testing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // AppIconResources (L05 KM-09b) and LocalPlatformActions (L18 IO-03b) live in core.
            implementation(project(":shared:core"))
            // ItmoTheme hands MaterialTheme to every module that renders the kit.
            api(libs.compose.material3)
            // Seed -> scheme and harmonize in commonMain (SP-06 way (b)); moves with every material3 bump.
            implementation(libs.material.kolor)
        }
        androidMain.dependencies {
            // JetBrains 1.12.0-alpha03 only `requires` this mapping; a newer Jetpack material3 anywhere on the app
            // classpath would replace it at runtime and break the kit's bytecode (SP-04, ADR 0021).
            val jetpackMaterial3 = libs.androidx.compose.material3.get()
            implementation(jetpackMaterial3.module.toString()) {
                version { strictly(jetpackMaterial3.versionConstraint.requiredVersion) }
            }
            // Fragment.itmoComposeView (host/); the app's own Fragment version, nothing newer.
            implementation(libs.androidx.fragment.ktx)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
        getByName("androidHostTest").dependencies {
            // The View theme the parity tests compare against; never on a main classpath of this module.
            implementation(libs.material)
        }
    }
}

// exportDesignTokens rewrites tokens/itmo-tokens.json (iOS and Web read it) from the Kotlin tokens through
// DesignTokensExportTest; every other host-test run compares the file and fails while it is stale.
val designTokensFile = layout.projectDirectory.file("tokens/itmo-tokens.json")
val exportDesignTokens = tasks.register("exportDesignTokens") {
    group = "build"
    description = "Rewrites tokens/itmo-tokens.json from the design system's Kotlin tokens."
    dependsOn("testAndroidHostTest")
}
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    // A file collection, so the first export runs before the file exists.
    inputs.files(designTokensFile).withPathSensitivity(PathSensitivity.RELATIVE)
}
gradle.taskGraph.whenReady {
    if (!hasTask(exportDesignTokens.get())) return@whenReady
    tasks.named<Test>("testAndroidHostTest").configure {
        filter.includeTestsMatching("*DesignTokensExportTest")
        systemProperty("designTokens.write", "true")
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}

// Shared icons stay Android drawables of :app for its layouts and R.drawable.ic_*; whoever removes the last such
// user in app/ removes this line (L05 KM-09a).
itmowidgetsStrings {
    androidExportDrawables()
}
