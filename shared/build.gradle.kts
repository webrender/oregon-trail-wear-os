import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

/** Must track the `org.jetbrains.compose` plugin version in the root build script. */
val composeVersion = "1.11.1"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // The browser build is an application, not a library: `binaries.executable()` is
        // what produces the `.wasm` and its JS loader, and without it the wasm target
        // compiles klibs that nothing ever links.
        binaries.executable()
        browser {
            commonWebpackConfig {
                outputFileName = "oregontrail.js"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }

        androidMain.dependencies {
            // Wear Compose is Android-only, which is the whole reason the widget layer is
            // expect/actual rather than shared. Pinned at the version the watch UI was
            // laid out against — every measurement in ui/components is a Chip metric from
            // this release, so a bump is a layout change, not a patch.
            implementation("androidx.wear.compose:compose-material:1.3.1")
            implementation("androidx.wear.compose:compose-foundation:1.3.1")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }

        // The game engine is pure Kotlin, so its tests run on the JVM as they always
        // have — plain JUnit, no emulator, no browser. They live in the Android target's
        // unit tests rather than in commonTest because that is the only target here with
        // a JVM to run them on, and moving ~150 tests to kotlin.test would be churn with
        // nothing to show for it.
        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

android {
    // Deliberately *not* `com.oregontrail.wear`, which is `:app`'s. Two Android modules
    // sharing a namespace generate two `R` classes with the same fully-qualified name,
    // and the one that wins is whichever the compiler sees first.
    namespace = "com.oregontrail.wear.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    // Spelled out rather than left to the default `src/main`, so the Android-only files
    // sit alongside the Android-only Kotlin rather than in a directory whose name says
    // nothing about which target it belongs to.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Tell Gradle that the unit tests read the art assets.
 *
 * `ArtNamesTest` opens the art directory directly as files, which Gradle cannot see:
 * assets are not on the unit test classpath, so nothing in the task's declared inputs
 * changes when an asset does. The result was a test task that reported UP-TO-DATE across
 * seventy newly added assets and never validated any of them — a build-time guard that
 * was silently not running. Declaring the directory as an input restores the guarantee
 * the art brief claims.
 */
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/androidMain/assets"))
        .withPropertyName("gameArtAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

/**
 * Stage the art where the browser build can serve it.
 *
 * The web build fetches each PNG over HTTP on demand rather than bundling the set — see
 * `ArtLoader` in `wasmJsMain`. That means the files have to end up in the webpack output
 * next to the wasm, and they cannot simply be copied into `src/wasmJsMain/resources` and
 * committed: they are already in `src/androidMain/assets`, and 20MB of art committed twice
 * is 20MB of art committed twice. So they are staged into the build directory and that
 * directory is registered as a resource root below, which is what carries them through
 * `wasmJsProcessResources` into the distribution.
 *
 * Registering the *task provider* as the source directory rather than its output path is
 * what makes Gradle infer the dependency, so nothing here has to name a task by string or
 * copy anything at execution time.
 */
val stageWebArt by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/androidMain/assets"))
    into(layout.buildDirectory.dir("webArt"))
}

kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(stageWebArt)
}
