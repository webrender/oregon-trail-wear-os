plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10" apply false
    // Versions must track the Kotlin plugin above. Since Kotlin 2.0 the Compose compiler
    // ships with Kotlin itself rather than as a separately versioned AGP extension, which
    // is why `composeOptions.kotlinCompilerExtensionVersion` is gone from app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false

    // Compose Multiplatform. Supplies the `org.jetbrains.compose.*` artifacts, which on
    // Android resolve to the ordinary `androidx.compose.*` ones — so the watch build gets
    // the same Compose it always had, and the browser build gets the Skia-backed one.
    id("org.jetbrains.compose") version "1.11.1" apply false
}
