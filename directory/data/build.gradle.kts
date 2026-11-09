/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.directory.data" }

dependencies {
    api(project(":directory:domain"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.bundles.coroutines)
}
