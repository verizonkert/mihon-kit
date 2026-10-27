plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
}

android {
    namespace = "mihon.kit.tester"

    defaultConfig {
        applicationId = "mihon.kit.tester"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":mihon-kit"))
    implementation(projects.sourceApi)
    implementation(libs.bundles.kotlinx.coroutines)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(projects.icons.materialSymbols)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
