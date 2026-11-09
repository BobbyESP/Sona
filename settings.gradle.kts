/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
pluginManagement {
    includeBuild("build-logic")
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
        google()
        mavenCentral()
        // Belledonne's repository serves the Linphone SDK, and nothing else is taken from it.
        maven("https://download.linphone.org/maven_repository") {
            content { includeGroupByRegex("org\\.linphone.*") }
        }
    }
}

rootProject.name = "Sona"

include(":app")

// Modules arrive with the phase that needs them (docs/roadmap.md). The full list of what is
// planned is in AGENTS.md.

// Core: what every module builds on
include(":core:model")

include(":core:common")

include(":core:data")

include(":core:designsystem")

include(":core:ui")

include(":core:navigation")

include(":core:testing")

// Engines: a contract (api) and its implementations
include(":sip:api")

include(":sip:fake")

// Features: what the user sees
include(":feature:history")

include(":feature:contacts")

include(":feature:dialer")
