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

include(":core:database")

include(":core:designsystem")

include(":core:ui")

include(":core:navigation")

include(":core:testing")

// Accounts: the SIP accounts several features share
include(":accounts:domain")

include(":accounts:data")

include(":accounts:ui")

// Calling: the coordinator several features share
include(":calling:domain")

include(":calling:ui")

include(":calling:platform")

// Directory: history and contacts, which the dialer, Recents and Contacts share
include(":directory:domain")

include(":directory:data")

include(":directory:ui")

// Engines: a contract (api) and its implementations
include(":sip:api")

include(":sip:linphone")

include(":sip:fake")

include(":credentials:api")

include(":credentials:keystore")

include(":telecom:api")

include(":telecom:jetpack")

// Features: what the user sees
include(":feature:settings")

include(":feature:diagnostics")

include(":feature:history")

include(":feature:contacts")

include(":feature:dialer")

include(":feature:accounts")

include(":feature:onboarding")

include(":feature:call")
