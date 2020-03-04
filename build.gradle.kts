import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("nebula.release") version "13.2.1"
}

group = "org.gradle"
description = "Refactor improve Spring usage automatically"

repositories {
    mavenLocal()
    maven { url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local") }
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

dependencies {
    implementation("com.netflix.devinsight.rewrite:rewrite-java:latest.integration")

    implementation("org.springframework:spring-beans:5.2.3.RELEASE")
    implementation("org.springframework:spring-webmvc:5.2.3.RELEASE")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("com.github.marschall:memoryfilesystem:latest.release")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.0.13")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

publishing {
    publications {
        create<MavenPublication>("jar") {
            artifactId = "rewrite-spring"
            artifact(tasks.named<Jar>("jar").get())
        }
    }
    repositories {
        maven {
            name = "GradleBuildInternalSnapshots"
            url = URI.create("https://repo.gradle.org/gradle/libs-snapshots-local")
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}
