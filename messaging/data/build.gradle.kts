/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.messaging.data" }

dependencies {
    api(project(":messaging:domain"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.bundles.coroutines)
    testImplementation(libs.junit)
}
