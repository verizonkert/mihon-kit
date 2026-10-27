plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

val releaseVersion = (project.findProperty("VERSION_NAME") as? String)
    ?: (project.version as? String)?.takeIf { it != "unspecified" }
    ?: "1.0.4"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.verizonkert.mihon-kit"
                artifactId = "source-api"
                version = releaseVersion
            }
        }
    }
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.injekt)
    implementation(libs.rxJava)
    implementation(libs.jsoup)

    implementation(libs.androidx.preference)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
