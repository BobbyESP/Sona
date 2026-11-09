plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

// compileOnly: the root build puts these plugins on the classpath (`apply false`), so the
// conventions apply the very same versions every module would.
dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

ktfmt { kotlinLangStyle() }

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sona.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sona.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "sona.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "sona.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "sona.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "sona.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
