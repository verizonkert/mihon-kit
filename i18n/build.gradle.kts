import mihon.gradle.tasks.GenerateLocalesConfigTask

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n"
    }

    dependencies {
        api(libs.moko.resources)
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateLocalesConfigTask>("generate${variantName}LocalesConfig") {
            localeFiles.from(
                layout.projectDirectory.dir("src/commonMain/moko-resources").asFileTree.matching {
                    include("**/strings.xml", "**/plurals.xml")
                },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
    }
}

multiplatformResources {
    resourcesPackage.set("tachiyomi.i18n")
}

