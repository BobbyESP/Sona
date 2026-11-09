/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins { id("sona.jvm.library") }

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
