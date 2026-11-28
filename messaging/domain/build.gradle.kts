/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":sip:api"))
    api(project(":accounts:domain"))
    api(libs.kotlinx.coroutines.core)
    // A file offer's description travels as JSON.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(project(":sip:fake"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
