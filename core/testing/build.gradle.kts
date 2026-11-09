/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.jvm.library") }

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
