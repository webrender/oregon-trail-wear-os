plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The release-signing material and the published version number, supplied as Gradle
// properties. `.github/workflows/release.yml` passes them from the repository's secrets
// when a tag is pushed; an ordinary local build passes none of them and gets the debug
// key and the placeholder version below, exactly as before.
val releaseKeystore = findProperty("keystore") as String?
val releaseKeystorePassword = findProperty("keystorePassword") as String?
val releaseKeyAlias = findProperty("keyAlias") as String?
val releaseKeyPassword = findProperty("keyPassword") as String?

android {
    namespace = "com.oregontrail.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oregontrail.wear"
        // Pixel Watch 2 ships Wear OS 4 (API 33).
        minSdk = 33
        targetSdk = 34
        // Placeholders for local builds. A published build overrides both: the name comes
        // from the git tag, and the code from the workflow run number — which only ever
        // increases, so a release can never be refused as a downgrade of the one before
        // it. Deriving the code from the tag instead would mean agreeing a scheme that
        // survives 0.9 → 0.10, and there is nothing to gain from it.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1"
    }

    signingConfigs {
        // Only declared when the material is actually present, so that a build without it
        // fails at `getByName("release")` below rather than producing an APK signed with
        // an empty key.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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

            // A published build is signed with the project's own key; a local one falls
            // back to the debug key, so `assembleRelease` still produces something that
            // installs straight onto the watch without anyone holding the real key. The
            // alternative to both is an unsigned APK that cannot be sideloaded at all,
            // which would mean the only *testable* build is the debug one — precisely the
            // problem this build type exists to fix.
            //
            // The two signatures are not interchangeable on a watch: Android identifies an
            // app by its certificate, so a locally built APK will not install over a
            // downloaded release, or the reverse, without uninstalling first. That is a
            // one-time annoyance for whoever develops the game and the correct behaviour
            // for everyone else, who only ever sees the released signature.
            signingConfig =
                if (releaseKeystore != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    lint {
        // `lintVitalRelease` crashes inside lint itself on this machine — it dies with a
        // bare version string while analysing, which is lint meeting a build-tools release
        // newer than the platform this project compiles against. It is a toolchain
        // mismatch, not a finding: nothing is reported, the task simply throws. Since it
        // runs only on release builds, leaving it on would mean the release APK — the one
        // worth shipping and profiling — cannot be built at all. `./gradlew lint` still
        // analyses the debug variant.
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // The game. Engine, controller, art, and every screen — see `:shared`, which builds
    // the same code for the browser.
    implementation(project(":shared"))

    // Installs the baseline profile that AGP merges out of the Compose and Wear Compose
    // AARs and packs into the APK at `assets/dexopt/baseline.prof`. Without it that file
    // is inert on a sideloaded build: `adb install` takes only the APK, not the `.dm`
    // the platform installer would otherwise read, so ART has no profile to compile
    // against and the whole Compose runtime stays interpreted until JIT catches up.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.activity:activity-compose:1.9.0")
}
