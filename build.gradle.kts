import io.spring.gradle.bintray.SpringBintrayExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import java.util.*
import nebula.plugin.info.InfoBrokerPlugin
import nebula.plugin.contacts.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jfrog.gradle.plugin.artifactory.dsl.*
import com.github.jk1.license.LicenseReportExtension

buildscript {
    repositories {
        jcenter()
        gradlePluginPortal()
    }

    dependencies {
        classpath("io.spring.gradle:spring-release-plugin:0.20.1")

        constraints {
            classpath("org.jfrog.buildinfo:build-info-extractor-gradle:4.13.0") {
                because("Need recent version for Gradle 6+ compatibility")
            }
        }
    }
}

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version "1.4.21"
    id("io.spring.release") version "0.20.1"
    id("com.github.jk1.dependency-license-report") version "1.16"
}

apply(plugin = "license")
apply(plugin = "nebula.maven-resolved-dependencies")
apply(plugin = "io.spring.publishing")

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

repositories {
    mavenLocal()
    maven { url = uri("https://dl.bintray.com/openrewrite/maven") }
    mavenCentral()
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
    implementation("org.openrewrite:rewrite-java:latest.integration")
    implementation("org.openrewrite:rewrite-xml:latest.integration")
    implementation("org.openrewrite:rewrite-properties:latest.integration")
    implementation("org.openrewrite:rewrite-yaml:latest.integration")
    implementation("org.openrewrite:rewrite-maven:latest.integration")

    // for locating list of released Spring Boot versions
    implementation("com.squareup.okhttp3:okhttp:latest.release")

    implementation("org.springframework:spring-beans:5.2.8.RELEASE")
    implementation("org.springframework:spring-webmvc:5.2.8.RELEASE")

    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:latest.integration")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.openrewrite:rewrite-test:latest.integration")
    testRuntimeOnly("org.openrewrite:rewrite-java-11:latest.integration")

    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("com.github.marschall:memoryfilesystem:latest.release")

    // for generating properties migration configurations
    testImplementation("com.fasterxml.jackson.core:jackson-databind:latest.release")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:latest.release")
    testImplementation("io.github.classgraph:classgraph:latest.release")

    // to test SpringBootServletInitializerTest
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:1.5.22.RELEASE")

    testRuntimeOnly("org.openrewrite:rewrite-java-11:latest.integration")
    testRuntimeOnly("org.openrewrite:rewrite-java-8:latest.integration")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.0.13")
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

configure<LicenseExtension> {
    ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
    skipExistingHeaders = true
    header = project.rootProject.file("gradle/licenseHeader.txt")
    mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
    strictCheck = true
}

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
                                        .item(0) as org.w3c.dom.Element).textContent == "org.jetbrains.kotlin") {
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

configure<SpringBintrayExtension> {
    org = "openrewrite"
    repo = "maven"
}

project.withConvention(ArtifactoryPluginConvention::class) {
    setContextUrl("https://oss.jfrog.org/artifactory")
    publisherConfig.let {
        val repository: PublisherConfig.Repository = it.javaClass
                .getDeclaredField("repository")
                .apply { isAccessible = true }
                .get(it) as PublisherConfig.Repository

        repository.setRepoKey("oss-snapshot-local")
        repository.setUsername(project.findProperty("bintrayUser"))
        repository.setPassword(project.findProperty("bintrayKey"))
    }
}

tasks.withType<GenerateMavenPom> {
    doLast {
        // because pom.withXml adds blank lines
        destination.writeText(
                destination.readLines().filter { it.isNotBlank() }.joinToString("\n")
        )
    }

    doFirst {
        val runtimeClasspath = configurations.getByName("runtimeClasspath")

        val gav = { dep: ResolvedDependency ->
            "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}"
        }

        val observedDependencies = TreeSet<ResolvedDependency> { d1, d2 ->
            gav(d1).compareTo(gav(d2))
        }

        fun reduceDependenciesAtIndent(indent: Int):
                (List<String>, ResolvedDependency) -> List<String> =
                { dependenciesAsList: List<String>, dep: ResolvedDependency ->
                    dependenciesAsList + listOf(" ".repeat(indent) + dep.module.id.toString()) + (
                            if (observedDependencies.add(dep)) {
                                dep.children
                                        .sortedBy(gav)
                                        .fold(emptyList(), reduceDependenciesAtIndent(indent + 2))
                            } else {
                                // this dependency subtree has already been printed, so skip it
                                emptyList()
                            }
                            )
                }

        project.plugins.withType<InfoBrokerPlugin> {
            add("Resolved-Dependencies", runtimeClasspath
                    .resolvedConfiguration
                    .lenientConfiguration
                    .firstLevelModuleDependencies
                    .sortedBy(gav)
                    .fold(emptyList(), reduceDependenciesAtIndent(6))
                    .joinToString("\n", "\n", "\n" + " ".repeat(4)))
        }
    }
}
