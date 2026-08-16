pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS. Both ignore repositories declared
    // by a project, which is the guarantee worth having; the difference is that
    // FAIL_ON_PROJECT_REPOS also refuses to build when one exists at all — and the Kotlin
    // plugin unconditionally adds two to the *root* project for the Wasm toolchain
    // (`kotlinWasmNodeJsSetup`, `kotlinBinaryenSetup`). Declaring the same two below is
    // not enough to satisfy that mode: it objects to their existence, not to their being
    // unresolvable. So the mode relaxes by one notch and the repositories move here.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // Kotlin/Wasm's own toolchain, which is fetched rather than resolved from Maven:
        // `kotlinWasmNodeJsSetup` downloads a Node distribution to run webpack, and
        // `kotlinBinaryenSetup` downloads Binaryen to optimise the `.wasm`. The Kotlin
        // plugin adds both repositories at *project* level, which is exactly what
        // FAIL_ON_PROJECT_REPOS above rejects — so they are declared here instead. The
        // alternative is relaxing the mode, which would also quietly re-permit any
        // repository a plugin cared to add.
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn releases"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/webassembly/binaryen/releases/download") {
            name = "Binaryen releases"
            patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "OregonTrailWear"

// The game itself — engine, controller, and every screen — lives here, built for both
// the watch and the browser. See docs/adr/0007-web-port.md.
include(":shared")

// The Wear OS app: an activity, a manifest, and an icon. Everything else is `:shared`.
include(":app")
