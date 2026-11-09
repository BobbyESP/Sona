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
        versionName = "0.2.0"
        // Phones and the emulator. Linphone ships native code for four ABIs; the two 32-bit ones
        // only add size for devices no lab has.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildFeatures { buildConfig = true }

    // Which SIP engine the app runs on. `full` is Linphone, for real servers. `demo` is the fake
    // engine, which accepts any account after a moment: the app runs with no server at all.
    flavorDimensions += "engine"
    productFlavors {
        create("full") { dimension = "engine" }
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
    implementation(project(":feature:accounts"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:call"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:diagnostics"))

    implementation(project(":calling:domain"))
    implementation(project(":calling:platform"))
    implementation(project(":telecom:jetpack"))

    implementation(project(":accounts:domain"))
    implementation(project(":accounts:data"))
    implementation(project(":accounts:ui"))
    implementation(project(":directory:domain"))
    implementation(project(":directory:data"))
    implementation(project(":credentials:keystore"))
    implementation(project(":core:database"))

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    implementation(project(":sip:api"))
    "fullImplementation"(project(":sip:linphone"))
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
