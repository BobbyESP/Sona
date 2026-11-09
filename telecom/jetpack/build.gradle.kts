/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.telecom.jetpack" }

dependencies {
    api(project(":telecom:api"))
    // `implementation`: no Core-Telecom type leaves this module.
    implementation(libs.androidx.core.telecom)
    implementation(libs.bundles.coroutines)
}
