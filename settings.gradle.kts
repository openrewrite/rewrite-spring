plugins {
    id("com.gradle.enterprise") version "3.1"
}

apply(from = "gradle/build-cache-configuration.settings.gradle.kts")

rootProject.name = "rewrite-security-bugs"
