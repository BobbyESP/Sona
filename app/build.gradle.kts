/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
plugins {
    id("sona.android.application")
    id("sona.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

// The release keystore comes from the environment. The release workflow decodes it, and a local
// release build without it is left unsigned. The key is never in the repository.
val signingStorePath: String? = System.getenv("SIGNING_KEY_STORE_PATH")

android {
    namespace = "com.bobbyesp.sona"

    defaultConfig {
        applicationId = "com.bobbyesp.sona"
        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
        // Phones and the emulator. Linphone ships native code for four ABIs. `armeabi-v7a` is kept
        // for older 32-bit ARM phones; only 32-bit x86 (no phone, no current emulator) is left out.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        // Where this app's source is published: the GPLv3 owes it to whoever has the app, and
        // About links to it. To be the real address once the repository exists.
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/BobbyESP/Sona\"")
    }

    // One APK per ABI (small, for whoever knows their phone) and a universal one with all of them
    // (for whoever does not). The app bundle is built without them (-PnoAbiSplits): Google Play
    // splits it on its own, and AGP refuses to build both in one run.
    splits {
        abi {
            isEnable = !project.hasProperty("noAbiSplits")
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures { buildConfig = true }

    // The app's languages, for Android's per-app language setting.
    androidResources { generateLocaleConfig = true }

    dependenciesInfo {
        // F-Droid cannot read the signed blob Google adds.
        includeInApk = false
        includeInBundle = false
    }

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

    signingConfigs {
        if (signingStorePath != null) {
            create("release") {
                storeFile = file(signingStorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
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
            if (signingStorePath != null) signingConfig = signingConfigs.getByName("release")
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
    implementation(project(":feature:messages"))

    implementation(project(":calling:domain"))
    implementation(project(":calling:platform"))
    implementation(project(":telecom:jetpack"))

    implementation(project(":accounts:domain"))
    implementation(project(":accounts:data"))
    implementation(project(":accounts:ui"))
    implementation(project(":directory:domain"))
    implementation(project(":directory:data"))
    implementation(project(":messaging:domain"))
    implementation(project(":messaging:data"))
    implementation(project(":messaging:platform"))
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

    // The screenshot tour: the demo app driven on a device (docs/releasing.md).
    "androidTestDemoImplementation"(libs.androidx.test.runner)
    "androidTestDemoImplementation"(libs.androidx.test.ext.junit)
    "androidTestDemoImplementation"(libs.androidx.test.rules)
    "androidTestDemoImplementation"(libs.androidx.test.uiautomator)
}
