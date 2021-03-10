rootProject.name = "rewrite-spring"

enableFeaturePreview("VERSION_ORDERING_V2")

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "org.openrewrite") {
                useModule("org.openrewrite:gradle-openrewrite-project-plugin:${requested.version}")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}
