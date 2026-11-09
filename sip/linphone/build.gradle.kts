/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.sip.linphone" }

dependencies {
    api(project(":sip:api"))
    // `implementation`: no Linphone type leaves this module.
    implementation(libs.linphone.sdk)
    implementation(libs.bundles.coroutines)
    testImplementation(libs.junit)
}
