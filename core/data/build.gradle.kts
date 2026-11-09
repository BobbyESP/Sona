/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.core.data" }

dependencies {
    api(project(":core:model"))
    implementation(libs.datastore.preferences)
    implementation(libs.bundles.coroutines)
}
