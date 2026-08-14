plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.oregontrail.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oregontrail.wear"
        // Pixel Watch 2 ships Wear OS 4 (API 33).
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // No native code, so no abiFilters: the APK is architecture-independent and
    // installs on the watch regardless of which ABIs it reports. This is why the
    // emulator build needed `armeabi-v7a` and this one doesn't.

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Signed with the debug key so `assembleRelease` produces something that
            // installs straight onto the watch. This is a game with no accounts and no
            // Play listing; the alternative is an unsigned APK that cannot be sideloaded,
            // which would mean the only *testable* build is the debug one — and that is
            // precisely the problem this build type exists to fix. Swap in a real key if
            // this ever ships.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // `lintVitalRelease` crashes inside lint itself on this machine — it dies with a
        // bare "25.0.2" while analysing, which is AGP 8.5.2's lint meeting the build-tools
        // 36 that Android Studio installed alongside the 34 this project compiles against.
        // It is a toolchain mismatch, not a finding: nothing is reported, the task simply
        // throws. Since it runs only on release builds, leaving it on would mean the
        // release APK — the one worth shipping and profiling — cannot be built at all.
        // `./gradlew lint` still analyses the debug variant.
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Tell Gradle that the unit tests read the art assets.
 *
 * `ArtNamesTest` opens `src/main/assets/art` directly as files, which Gradle cannot see:
 * assets are not on the unit test classpath, so nothing in the task's declared inputs
 * changes when an asset does. The result was a test task that reported UP-TO-DATE across
 * seventy newly added assets and never validated any of them — a build-time guard that
 * was silently not running. Declaring the directory as an input restores the guarantee
 * the art brief claims.
 */
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/assets"))
        .withPropertyName("gameArtAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Installs the baseline profile that AGP merges out of the Compose and Wear Compose
    // AARs and packs into the APK at `assets/dexopt/baseline.prof`. Without it that file
    // is inert on a sideloaded build: `adb install` takes only the APK, not the `.dm`
    // the platform installer would otherwise read, so ART has no profile to compile
    // against and the whole Compose runtime stays interpreted until JIT catches up.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // The game core is pure Kotlin with no Android dependencies, so it's tested
    // on the JVM — no emulator in the iteration loop.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
