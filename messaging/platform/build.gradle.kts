/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.android.library") }

android { namespace = "com.bobbyesp.sona.messaging.platform" }

// The notifications of messages (MessagingStyle, inline reply, mark as read), from the Messenger's
// events. Only :app depends on it. Filled with the Messages screens.
dependencies {
    api(project(":messaging:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.coroutines)
    implementation(libs.koin.android)
}
