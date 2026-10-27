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

val releaseVersion = (project.findProperty("VERSION_NAME") as? String)
    ?: (project.version as? String)?.takeIf { it != "unspecified" }
    ?: "1.0.4"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.verizonkert.mihon-kit"
                artifactId = "mihon-kit"
                version = releaseVersion

                pom.withXml {
                    val dependenciesNode = asNode().get("dependencies") as? groovy.util.Node ?: return@withXml
                    dependenciesNode.children().filterIsInstance<groovy.util.Node>().forEach { dep ->
                        val artifactIdNode = dep.get("artifactId") as? groovy.util.NodeList
                        val groupIdNode = dep.get("groupId") as? groovy.util.NodeList
                        val versionNode = dep.get("version") as? groovy.util.NodeList

                        val artifactId = artifactIdNode?.text()
                        val currentVersion = version ?: releaseVersion
                        if (artifactId == "source-api") {
                            groupIdNode?.set(0, "com.github.verizonkert.mihon-kit")
                            versionNode?.set(0, currentVersion)
                        } else if (artifactId == "common") {
                            groupIdNode?.set(0, "com.github.verizonkert.mihon-kit")
                            artifactIdNode?.set(0, "core-common")
                            versionNode?.set(0, currentVersion)
                        }
                    }
                }
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
