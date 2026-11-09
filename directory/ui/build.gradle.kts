/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.library")
    id("sona.android.compose")
}

android { namespace = "com.bobbyesp.sona.directory.ui" }

dependencies {
    api(project(":directory:domain"))
    api(project(":core:designsystem"))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
