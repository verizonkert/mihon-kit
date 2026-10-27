plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"

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
                artifactId = "core-common"
                version = releaseVersion
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.core.metro)
    implementation(projects.i18n)

    api(libs.logcat)

    api(libs.rxJava)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okio)

    api(libs.apollo)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.archive)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    api(libs.androidx.preference)
    implementation(libs.androidx.webkit)

    implementation(libs.jsoup)

    // Sort
    implementation(libs.natural.comparator)

    // JavaScript engine
    implementation(libs.quickJs)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.metro.runtime)
}
