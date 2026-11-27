import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature: its screens, ViewModels, its own domain if any, and its DI. It brings what every
 * feature builds on, so a feature's build file names only what is its own.
 *
 * It also fails the build when a feature depends on a module it must not see: another feature, a
 * storage module, the calling platform (services, notifications) or an engine's implementation. A
 * feature that sees any of those can no longer be built, tested or replaced on its own.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("sona.android.library")
            pluginManager.apply("sona.android.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.bundle("lifecycle"))
                add("implementation", libs.bundle("compose"))
                add("implementation", libs.bundle("coroutines"))
                add("implementation", libs.bundle("koin"))
                add("implementation", libs.bundle("navigation3"))
                add("implementation", libs.library("kotlinx-serialization-json"))
                add("implementation", libs.library("kotlinx-collections-immutable"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
            }

            afterEvaluate { checkDependencyRules() }
        }
    }

    /**
     * What a feature may see is listed, not what it may not: a new engine implementation, or a new
     * feature, is refused without anyone having to remember to add it here.
     */
    private fun Project.checkDependencyRules() {
        val forbidden =
            configurations
                .asSequence()
                .flatMap { configuration ->
                    configuration.dependencies.withType(ProjectDependency::class.java).map {
                        configuration.name to it.path
                    }
                }
                // AGP makes a module's test configurations depend on the module itself.
                .filter { (_, dependency) -> dependency != path }
                // Tests use fakes, never implementations: a fake engine is what tests are for.
                .filterNot { (configuration, dependency) ->
                    configuration.isTestConfiguration() && dependency.endsWith(":fake")
                }
                .map { (_, dependency) -> dependency }
                .filterNot { it.isAllowedInFeature() }
                .distinct()
                .sorted()
                .toList()
        if (forbidden.isNotEmpty()) {
            throw GradleException(
                "$path depends on $forbidden. A feature sees :core:* (never :core:data or " +
                    ":core:database), :accounts:*, :calling:*, :directory:* and :messaging:* " +
                    "(never their :data or :platform) and the engines' contracts (*:api). It " +
                    "opens another feature by " +
                    "a key of :core:navigation, adds to its screens through a contribution, and " +
                    "implementations are bound in :app."
            )
        }
    }

    private fun String.isTestConfiguration(): Boolean =
        startsWith("test") || startsWith("androidTest") || contains("UnitTest")

    private fun String.isAllowedInFeature(): Boolean =
        when {
            this == ":core:data" || this == ":core:database" -> false
            endsWith(":data") || endsWith(":platform") -> false
            startsWith(":core:") -> true
            startsWith(":accounts:") ||
                startsWith(":calling:") ||
                startsWith(":directory:") ||
                startsWith(":messaging:") -> true
            endsWith(":api") -> true
            else -> false
        }
}
