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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Local cache first: lets offline/air-gapped machines resolve Kotlin
        // compiler artifacts that are already present in ~/.m2.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "GexRDP-KVM"
include(":app")
