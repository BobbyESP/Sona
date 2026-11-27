/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.library")
    id("sona.android.compose")
}

android { namespace = "com.bobbyesp.sona.messaging.ui" }

// What the messaging domain says in words (statuses, failures with their SIP line) and the pieces
// several screens share. Filled with the Messages screens.
dependencies {
    api(project(":messaging:domain"))
    api(project(":core:designsystem"))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
