/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.application")
    id("sona.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bobbyesp.sona"

    defaultConfig {
        applicationId = "com.bobbyesp.sona"
        versionCode = 1
        versionName = "0.1.0"
    }

    // Which SIP engine the app runs on. `demo` is the fake engine, with a demo account that
    // registers: no server needed. `full` (Linphone) arrives with phase 1.
    flavorDimensions += "engine"
    productFlavors {
        create("demo") {
            dimension = "engine"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug { applicationIdSuffix = ".debug" }
    }
}

dependencies {
    // The features. The app is their shell: it starts them, draws the one back stack they share,
    // and chooses the engines behind their ports.
    implementation(project(":feature:history"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:dialer"))

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    implementation(project(":sip:api"))
    "demoImplementation"(project(":sip:fake"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.navigation3)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(project(":core:testing"))
    testImplementation(project(":sip:fake"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
