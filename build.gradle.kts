import com.github.jk1.license.LicenseReportExtension
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "latest.release"
    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"

    id("com.github.hierynomus.license") version "0.16.1"
    id("com.github.jk1.dependency-license-report") version "1.16"

    id("nebula.maven-publish") version "17.3.2"
    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "9.3.0"

    id("nebula.javadoc-jar") version "17.3.2"
    id("nebula.source-jar") version "17.3.2"
    id("nebula.maven-apache-license") version "17.3.2"

    id("org.openrewrite.rewrite") version "latest.release"
}

apply(plugin = "nebula.publish-verification")

rewrite {
    rewriteVersion = "latest.integration"
    activeRecipe("org.openrewrite.java.format.AutoFormat", "org.openrewrite.java.cleanup.Cleanup")
}

configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

val springBoot2Versions: List<String> = listOf("1_5", "2_1", "2_2", "2_3", "2_4", "2_5")
val springDataVersions: List<String> = listOf("2_1", "2_3")
val springFrameworkVersions: List<String> = listOf("5_1", "5_2", "5_3")

sourceSets {
    springBoot2Versions.forEach { version ->
        create("testWithSpringBoot_${version}") {
            java {
                compileClasspath += sourceSets.getByName("main").output
                runtimeClasspath += sourceSets.getByName("main").output
            }
        }
    }
    springDataVersions.forEach { version ->
        create("testWithSpringData_${version}") {
            java {
                compileClasspath += sourceSets.getByName("main").output
                runtimeClasspath += sourceSets.getByName("main").output
            }
        }
    }
    springFrameworkVersions.forEach { version ->
        create("testWithSpringFramework_${version}") {
            java {
                compileClasspath += sourceSets.getByName("main").output
                runtimeClasspath += sourceSets.getByName("main").output
            }
        }
    }
}

repositories {
    if(!project.hasProperty("releasing")) {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

val signingKey: String? by project
val signingPassword: String? by project
val requireSigning = project.hasProperty("forceSigning") || project.hasProperty("releasing")
if(signingKey != null && signingPassword != null) {
    signing {
        isRequired = requireSigning
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["nebula"])
    }
} else if(requireSigning) {
    throw RuntimeException("Artifact signing is required, but signingKey and/or signingPassword are null")
}

configurations {
    springBoot2Versions.forEach { version ->
        getByName("testWithSpringBoot_${version}RuntimeOnly") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
        getByName("testWithSpringBoot_${version}Implementation") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
    }

    springDataVersions.forEach { version ->
        getByName("testWithSpringData_${version}RuntimeOnly") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
        getByName("testWithSpringData_${version}Implementation") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
    }

    springFrameworkVersions.forEach { version ->
        getByName("testWithSpringFramework_${version}RuntimeOnly") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
        getByName("testWithSpringFramework_${version}Implementation") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
    }

    all {
        resolutionStrategy {
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }
}

var rewriteVersion = if(project.hasProperty("releasing")) {
    "latest.release"
} else {
    "latest.integration"
}

dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation("org.openrewrite:rewrite-java:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-xml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-properties:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-yaml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-maven:${rewriteVersion}")

    // for locating list of released Spring Boot versions
    implementation("com.squareup.okhttp3:okhttp:latest.release")

    // eliminates "unknown enum constant DeprecationLevel.WARNING" warnings from the build log
    // see https://github.com/gradle/kotlin-dsl-samples/issues/1301 for why (okhttp is leaking parts of kotlin stdlib)
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:${rewriteVersion}")
    runtimeOnly("org.openrewrite:rewrite-java-11:$rewriteVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("org.openrewrite:rewrite-test:${rewriteVersion}")

    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("com.github.marschall:memoryfilesystem:latest.release")

    // for generating properties migration configurations
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.12.+")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.+")
    testImplementation("io.github.classgraph:classgraph:latest.release")

    testImplementation("org.openrewrite:rewrite-java-11:${rewriteVersion}")
    testImplementation("org.openrewrite:rewrite-java-8:${rewriteVersion}")

    testRuntimeOnly("org.springframework:spring-beans:latest.release")
    testRuntimeOnly("org.springframework:spring-context:latest.release")
    testRuntimeOnly("org.springframework:spring-web:latest.release")
    testRuntimeOnly("org.springframework:spring-test:latest.release")
    testRuntimeOnly("org.springframework.boot:spring-boot-test:latest.release")
    testRuntimeOnly("org.springframework.boot:spring-boot-test-autoconfigure:latest.release")

    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework:spring-web:4.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")

    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework:spring-web:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot:2.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.1.0.RELEASE")

    "testWithSpringBoot_2_2RuntimeOnly"("org.springframework.boot:spring-boot:2.2.+")

    "testWithSpringBoot_2_3RuntimeOnly"("org.openrewrite.recipe:rewrite-testing-frameworks:${rewriteVersion}")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-test:4.+")
    "testWithSpringBoot_2_3RuntimeOnly"("junit:junit:latest.release")
    "testWithSpringBoot_2_3RuntimeOnly"("org.hamcrest:hamcrest:2.2")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:2.3.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-web:5.2.+")

    "testWithSpringData_2_1RuntimeOnly"("org.springframework.data:spring-data-jpa:2.1.0.RELEASE")
    "testWithSpringData_2_1RuntimeOnly"("javax.persistence:javax.persistence-api:2.2")

    "testWithSpringData_2_3RuntimeOnly"("org.springframework.data:spring-data-jpa:2.3.0.RELEASE")
    "testWithSpringData_2_3RuntimeOnly"("javax.persistence:javax.persistence-api:2.2")

    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot:2.4.+")

    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework.boot:spring-boot:2.5.+")
    "testWithSpringBoot_2_5RuntimeOnly"("mysql:mysql-connector-java:8.0.27")
    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework:spring-context:2.5.+")
    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework.data:spring-data-jpa:2.5.+")
    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework.data:spring-jdbc:2.5.+")

    "testWithSpringFramework_5_1RuntimeOnly"("org.springframework:spring-core:5.1.+")

    "testWithSpringFramework_5_2RuntimeOnly"("org.springframework:spring-web:5.2.+")
    "testWithSpringFramework_5_2RuntimeOnly"("org.springframework:spring-core:5.2.+")

    "testWithSpringFramework_5_3RuntimeOnly"("org.springframework:spring-core:5.3.+")
    "testWithSpringFramework_5_3RuntimeOnly"("org.springframework:spring-beans:5.3.+")
    "testWithSpringFramework_5_3RuntimeOnly"("org.springframework:spring-tx:5.3.+")
    "testWithSpringFramework_5_3RuntimeOnly"("org.springframework:spring-jdbc:5.3.+")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
}

springBoot2Versions.forEach { version ->
    val sourceSetName = "testWithSpringBoot_${version}"
    val sourceSetReference = project.sourceSets.getByName(sourceSetName)
    val testTask = tasks.register<Test>(sourceSetName) {
        description = "Runs the unit tests for ${sourceSetName}."
        group = "verification"
        useJUnitPlatform()
        jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
        testClassesDirs = sourceSetReference.output.classesDirs
        classpath = sourceSetReference.runtimeClasspath
        shouldRunAfter(tasks.test)
    }
    tasks.test {
        dependsOn(testTask)
    }
}
springDataVersions.forEach { version ->
    val sourceSetName = "testWithSpringData_${version}"
    val sourceSetReference = project.sourceSets.getByName(sourceSetName)
    val testTask = tasks.register<Test>(sourceSetName) {
        description = "Runs the unit tests for ${sourceSetName}."
        group = "verification"
        useJUnitPlatform()
        jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
        testClassesDirs = sourceSetReference.output.classesDirs
        classpath = sourceSetReference.runtimeClasspath
        shouldRunAfter(tasks.test)
    }
    tasks.test {
        dependsOn(testTask)
    }
}
springFrameworkVersions.forEach { version ->
    val sourceSetName = "testWithSpringFramework_${version}"
    val sourceSetReference = project.sourceSets.getByName(sourceSetName)
    val testTask = tasks.register<Test>(sourceSetName) {
        description = "Runs the unit tests for ${sourceSetName}."
        group = "verification"
        useJUnitPlatform()
        jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
        testClassesDirs = sourceSetReference.output.classesDirs
        classpath = sourceSetReference.runtimeClasspath
        shouldRunAfter(tasks.test)
    }
    tasks.test {
        dependsOn(testTask)
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
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

 configure<LicenseExtension> {
     ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
     skipExistingHeaders = true
     header = project.rootProject.file("gradle/licenseHeader.txt")
     mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
     // exclude JavaTemplate shims from license check
     exclude("src/main/resources/META-INF/rewrite/*.java")
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
                                        .item(0) as org.w3c.dom.Element).textContent == "provided") {
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
