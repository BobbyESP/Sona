import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object ProjectConfig {
    /** Core-Telecom needs 24; 26 is Metadator's, and no lab phone is older. */
    const val minSdk = 26

    const val compileSdk = 37
    const val compileSdkMinor = 2

    const val targetSdk = 37

    val javaVersion = JavaVersion.VERSION_17
    val jvmTarget = JvmTarget.JVM_17
}
