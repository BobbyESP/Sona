/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.feature") }

android { namespace = "com.bobbyesp.sona.feature.accounts" }

dependencies {
    implementation(project(":accounts:ui"))
    implementation(project(":sip:api"))
    testImplementation(project(":sip:fake"))
}
