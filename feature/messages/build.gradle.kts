/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.feature") }

android { namespace = "com.bobbyesp.sona.feature.messages" }

dependencies {
    implementation(project(":messaging:domain"))
    implementation(project(":messaging:ui"))
    implementation(project(":accounts:domain"))
    implementation(project(":accounts:ui"))
    implementation(project(":directory:domain"))
    implementation(project(":directory:ui"))
    // The address resolver: a number typed for a message is read as the keypad reads it.
    implementation(project(":calling:domain"))
    implementation(libs.androidx.activity.compose)
    // Pictures in a conversation, decoded at the size they are shown.
    implementation(libs.coil.compose)
    testImplementation(project(":sip:fake"))
}
