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
            isMinifyEnabled = false
        }
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
 * `PixelArtTest` and `ArtNamesTest` both open `src/main/assets/art` directly as files,
 * which Gradle cannot see: assets are not on the unit test classpath, so nothing in the
 * task's declared inputs changes when a `.pix` file does. The result was a test task that
 * reported UP-TO-DATE across seventy newly added assets and never validated any of them —
 * a build-time guard that was silently not running. Declaring the directory as an input
 * restores the guarantee the art brief claims.
 */
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/assets"))
        .withPropertyName("gameArtAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
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
