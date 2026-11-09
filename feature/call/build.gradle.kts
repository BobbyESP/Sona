/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.feature") }

android { namespace = "com.bobbyesp.sona.feature.call" }

dependencies {
    api(project(":calling:domain"))
    implementation(project(":accounts:ui"))
    implementation(libs.androidx.activity.compose)
    testImplementation(project(":sip:fake"))
}
