plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "mihon.kit"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.verizonkert"
                artifactId = "mihon-kit"
                version = "1.0.0"
            }
        }
    }
}

dependencies {
    api(projects.sourceApi)
    api(projects.core.common)

    api(libs.okhttp.core)
    api(libs.okhttp.zstd)
    api(libs.okhttp.brotli)
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    api(libs.bundles.kotlinx.coroutines)
    api(libs.injekt)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(projects.icons.materialSymbols)

    implementation(libs.androidx.core)
    implementation(libs.androidx.preference)
}
