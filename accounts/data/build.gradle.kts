/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.accounts.data" }

dependencies {
    api(project(":accounts:domain"))
    implementation(project(":core:database"))
    implementation(libs.room.runtime)
    implementation(libs.bundles.coroutines)
}
