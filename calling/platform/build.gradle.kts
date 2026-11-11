/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.calling.platform" }

dependencies {
    api(project(":calling:domain"))
    api(project(":directory:domain"))
    // The readiness checks are contributions: their contract is there.
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.bundles.coroutines)
    implementation(libs.koin.android)
}
