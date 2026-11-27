pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Upstream libavif documents this official Maven Central endpoint.
        // Keep routing scoped to this single module in the research branch.
        exclusiveContent {
            forRepository {
                maven { url = uri("https://repo1.maven.org/maven2") }
            }
            filter { includeModule("org.aomedia.avif.android", "avif") }
        }
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven { url = uri("https://jitpack.io") }
            }
            filter { includeModule("com.github.Kotatsu-Redo", "kotatsu-parsers-redo") }
        }
        maven {
            url = uri("https://maven.mozilla.org/maven2")
            content { includeGroupByRegex("org\\.mozilla.*") }
        }
    }
}

rootProject.name = "Anilife"
include(":app")
include(":benchmark")

// Research module only; deliberately not a dependency of :app.
include(":manga-engine")
