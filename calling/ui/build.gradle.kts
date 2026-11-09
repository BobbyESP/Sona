/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.library")
    id("sona.android.compose")
}

android { namespace = "com.bobbyesp.sona.calling.ui" }

dependencies {
    api(project(":calling:domain"))
    implementation(libs.bundles.compose)
}
