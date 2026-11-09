/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.calling.platform" }

dependencies {
    api(project(":calling:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.bundles.coroutines)
    implementation(libs.koin.android)
}
