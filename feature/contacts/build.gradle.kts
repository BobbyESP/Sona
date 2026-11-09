/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.feature") }

android { namespace = "com.bobbyesp.sona.feature.contacts" }

dependencies {
    implementation(project(":calling:domain"))
    implementation(project(":calling:ui"))
    implementation(project(":directory:ui"))
    testImplementation(project(":sip:fake"))
}
