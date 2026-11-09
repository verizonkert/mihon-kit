package mihon.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope

/** Adds an `api` dependency from a type-safe version catalog provider. */
fun DependencyHandlerScope.api(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("api", dependencyNotation)
}

/** Adds a `coreLibraryDesugaring` dependency from a version catalog provider. */
fun DependencyHandlerScope.coreLibraryDesugaring(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("coreLibraryDesugaring", dependencyNotation)
}

/** Adds a `debugApi` dependency from a version catalog provider. */
fun DependencyHandlerScope.debugApi(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("debugApi", dependencyNotation)
}

/** Adds an `implementation` dependency bundle from a version catalog provider. */
@JvmName("implementationBundle")
fun DependencyHandlerScope.implementation(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", dependencyNotation)
}

/** Adds an `implementation` dependency from a version catalog provider. */
fun DependencyHandlerScope.implementation(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("implementation", dependencyNotation)
}

/** Adds an `implementation` dependency on another subproject module. */
fun DependencyHandlerScope.implementation(dependencyNotation: Project) {
    add("implementation", dependencyNotation)
}
