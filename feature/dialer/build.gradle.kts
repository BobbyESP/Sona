/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.feature") }

android { namespace = "com.bobbyesp.sona.feature.dialer" }

dependencies {
    implementation(project(":calling:domain"))
    implementation(libs.androidx.activity.compose)
    testImplementation(project(":sip:fake"))
}
