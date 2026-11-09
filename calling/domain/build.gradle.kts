/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.jvm.library") }

dependencies {
    api(project(":core:model"))
    api(project(":sip:api"))
    api(project(":telecom:api"))
    api(project(":accounts:domain"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":core:testing"))
    testImplementation(project(":sip:fake"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
