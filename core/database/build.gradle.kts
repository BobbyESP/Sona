/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.library")
    id("sona.android.room")
}

android { namespace = "com.bobbyesp.sona.core.database" }

dependencies {
    // `api`: whoever holds the database (the app, the *:data modules) holds a RoomDatabase.
    api(libs.room.runtime)
    implementation(libs.bundles.coroutines)
}
