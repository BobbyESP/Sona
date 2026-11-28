/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
    configure<SpotlessExtension> {
        kotlin {
            ktfmt().kotlinlangStyle()
            target("src/**/*.kt")
            targetExclude("${layout.buildDirectory}/**/*.kt")
            licenseHeaderFile(rootProject.file("spotless/copyright.txt"))
        }
        kotlinGradle {
            ktfmt().kotlinlangStyle()
            target("*.kts")
            targetExclude("${layout.buildDirectory}/**/*.kts")
            licenseHeaderFile(rootProject.file("spotless/copyright.txt"), "(^(?![\\/ ]\\*).*$)")
        }
        format("xml") {
            target("src/**/*.xml")
            targetExclude("**/build/", ".idea/")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

// The convention plugins are formatted with the rest.
listOf("spotlessCheck", "spotlessApply").forEach { name ->
    tasks.named(name) { dependsOn(gradle.includedBuild("build-logic").task(":convention:$name")) }
}

/**
 * The app's version. The code packs the stage in, so an alpha, a beta, a release candidate and the
 * release of one version are ordered: MmmPPsBB (major, minor, patch, stage 0-3, build).
 */
sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int) {
    abstract val stageCode: Int
    abstract val stageName: String

    fun toVersionName(): String =
        if (this is Stable) "$major.$minor.$patch" else "$major.$minor.$patch-$stageName.$build"

    fun toVersionCode(): Int =
        major * 10_000_000 + minor * 100_000 + patch * 1_000 + stageCode * 100 + build

    class Alpha(major: Int, minor: Int, patch: Int, build: Int) :
        Version(major, minor, patch, build) {
        override val stageCode = 0
        override val stageName = "alpha"
    }

    class Beta(major: Int, minor: Int, patch: Int, build: Int) :
        Version(major, minor, patch, build) {
        override val stageCode = 1
        override val stageName = "beta"
    }

    class ReleaseCandidate(major: Int, minor: Int, patch: Int, build: Int) :
        Version(major, minor, patch, build) {
        override val stageCode = 2
        override val stageName = "rc"
    }

    class Stable(major: Int, minor: Int, patch: Int) : Version(major, minor, patch, 0) {
        override val stageCode = 3
        override val stageName = ""
    }
}

// The one place the version is set. 1.0.0-beta.3 is code 10000103, below 1.0.0's 10000300.
val currentVersion: Version = Version.Beta(major = 1, minor = 0, patch = 0, build = 3)

extra.set("versionCode", currentVersion.toVersionCode())

extra.set("versionName", currentVersion.toVersionName())
