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
    implementation("com.netflix.devinsight.rewrite:rewrite-core:latest.integration")

    implementation("org.springframework:spring-beans:5.2.3.RELEASE")
    implementation("org.springframework:spring-webmvc:5.2.3.RELEASE")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")

    testImplementation("org.assertj:assertj-core:latest.release")

    // for testing ConstructorInjection
    testRuntimeOnly("org.projectlombok:lombok:1.18.10")
    testRuntimeOnly("com.google.code.findbugs:jsr305")
    testRuntimeOnly("javax.inject:javax.inject:1")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.0.13")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
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
