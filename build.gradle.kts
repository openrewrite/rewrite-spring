import com.github.jk1.license.LicenseReportExtension
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "1.4.21"
    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"

    id("com.github.hierynomus.license") version "0.15.0" apply false
    id("com.github.jk1.dependency-license-report") version "1.16"

    id("nebula.maven-publish") version "17.3.2"
    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "9.3.0"

    id("nebula.javadoc-jar") version "17.3.2"
    id("nebula.source-jar") version "17.3.2"
    id("nebula.maven-apache-license") version "17.3.2"
}

configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

repositories {
    mavenLocal()
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

signing {
    setRequired({
        !project.version.toString().endsWith("SNAPSHOT") || project.hasProperty("forceSigning")
    })
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["nebula"])
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }

    // We use kotlin exclusively for tests
    // The kotlin plugin adds kotlin-stdlib dependencies to the main sourceSet, even if it doesn't use any kotlin
    // To avoid shipping dependencies we don't actually need, exclude them from the main sourceSet classpath but add them _back_ in for the test source sets
    if (name == "compileClasspath" || name == "runtimeClasspath") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation("org.openrewrite:rewrite-java:latest.integration")
    implementation("org.openrewrite:rewrite-xml:latest.integration")
    implementation("org.openrewrite:rewrite-properties:latest.integration")
    implementation("org.openrewrite:rewrite-yaml:latest.integration")
    implementation("org.openrewrite:rewrite-maven:latest.integration")

    // for locating list of released Spring Boot versions
    implementation("com.squareup.okhttp3:okhttp:latest.release")

    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:latest.integration")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.openrewrite:rewrite-test:latest.integration")

    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("com.github.marschall:memoryfilesystem:latest.release")

    // for generating properties migration configurations
    testImplementation("com.fasterxml.jackson.core:jackson-databind:latest.release")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:latest.release")
    testImplementation("io.github.classgraph:classgraph:latest.release")

    testRuntimeOnly("org.openrewrite:rewrite-java-11:latest.integration")
    testRuntimeOnly("org.openrewrite:rewrite-java-8:latest.integration")

    testRuntimeOnly("junit:junit:latest.release")
    testRuntimeOnly("org.springframework:spring-test:4.+")
    testRuntimeOnly("org.springframework:spring-beans:4.+")
    testRuntimeOnly("org.springframework:spring-webmvc:4.+")
    testRuntimeOnly("org.springframework.boot:spring-boot-autoconfigure:1.5.+")
    testRuntimeOnly("org.springframework.boot:spring-boot-test:1.5.+")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.forkOptions.executable = "javac"
    options.compilerArgs.addAll(listOf("--release", "8"))
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }

    doFirst {
        destinationDir.mkdirs()
    }
}

configure<ContactsExtension> {
    val j = Contact("jkschneider@gmail.com")
    j.moniker("Jonathan Schneider")

    people["jkschneider@gmail.com"] = j
}

// configure<LicenseExtension> {
//     ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
//     skipExistingHeaders = true
//     header = project.rootProject.file("gradle/licenseHeader.txt")
//     mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
//     // exclude JavaTemplate shims from license check
//     exclude("src/main/resources/META-INF/rewrite/*.java")
//     strictCheck = true
// }

configure<LicenseReportExtension> {
    renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")

            pom.withXml {
                (asElement().getElementsByTagName("dependencies").item(0) as org.w3c.dom.Element).let { dependencies ->
                    dependencies.getElementsByTagName("dependency").let { dependencyList ->
                        var i = 0
                        var length = dependencyList.length
                        while (i < length) {
                            (dependencyList.item(i) as org.w3c.dom.Element).let { dependency ->
                                if ((dependency.getElementsByTagName("scope")
                                        .item(0) as org.w3c.dom.Element).textContent == "provided"
                                    || (dependency.getElementsByTagName("groupId")
                                        .item(0) as org.w3c.dom.Element).textContent == "org.jetbrains.kotlin"
                                ) {
                                    dependencies.removeChild(dependency)
                                    i--
                                    length--
                                }
                            }
                            i++
                        }
                    }
                }
            }
        }
    }
}
