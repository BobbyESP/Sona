import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.HasDeviceTestsBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** An Android library, without UI. A library that draws applies `sona.android.compose` as well. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> { configureKotlinAndroid(this) }

            // A module without device tests builds no test APK. Otherwise, every module would build
            // and dex one, most of them empty, and a full build ran out of memory doing it.
            val hasDeviceTests = hasDeviceTests()
            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants { variant ->
                    if (!hasDeviceTests) {
                        (variant as HasDeviceTestsBuilder)
                            .deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]
                            ?.enable = false
                    }
                }
            }

            if (hasDeviceTests) {
                dependencies {
                    add("androidTestImplementation", libs.library("androidx-test-runner"))
                }
            }
        }
    }
}
