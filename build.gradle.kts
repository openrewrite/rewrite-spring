plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

val springBootVersions: List<String> = listOf("1_5", "2_1", "2_2", "2_3", "2_4", "2_5", "2_6", "2_7", "3_0", "3_2", "3_3", "3_4")
val springSecurityVersions: List<String> = listOf("5_7", "5_8", "6_2")

val sourceSetNames: Map<String, List<String>> = mapOf(
    Pair("testWithSpringBoot_", springBootVersions),
    Pair("testWithSpringSecurity_", springSecurityVersions)
)

sourceSets {
    sourceSetNames.forEach { sourceSetName, versions ->
        versions.forEach { version ->
            create("${sourceSetName}${version}") {
                java {
                    compileClasspath += sourceSets.getByName("main").output
                    runtimeClasspath += sourceSets.getByName("main").output
                }
            }
        }
    }
}

repositories {
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}

configurations {
    sourceSetNames.forEach { sourceSetName, versions ->
        versions.forEach { version ->
            getByName("${sourceSetName}${version}RuntimeOnly") {
                isCanBeResolved = true
                extendsFrom(getByName("testRuntimeOnly"))
            }
            getByName("${sourceSetName}${version}Implementation") {
                isCanBeResolved = true
                extendsFrom(getByName("testImplementation"))
            }
        }
    }
}

recipeDependencies {

    parserClasspath("javax.persistence:javax.persistence-api:2.+")
    parserClasspath("javax.validation:validation-api:2.0.1.Final")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:latest.release")

    parserClasspath("org.springframework.boot:spring-boot:1.+")
    parserClasspath("org.springframework.boot:spring-boot:2.+")
    parserClasspath("org.springframework.boot:spring-boot:3.+")

    parserClasspath("org.springframework.boot:spring-boot-autoconfigure:2.+")
    parserClasspath("org.springframework.boot:spring-boot-actuator:2.+")
    parserClasspath("org.springframework.boot:spring-boot-actuator:2.5.+")
    parserClasspath("org.springframework.boot:spring-boot-test:2.+")

    parserClasspath("org.springframework:spring-beans:4.+")
    parserClasspath("org.springframework:spring-beans:5.+")
    parserClasspath("org.springframework:spring-beans:6.+")

    parserClasspath("org.springframework:spring-context:4.+")
    parserClasspath("org.springframework:spring-context:5.+")
    parserClasspath("org.springframework:spring-context:6.+")

    parserClasspath("org.springframework:spring-core:4.+")
    parserClasspath("org.springframework:spring-core:5.+")
    parserClasspath("org.springframework:spring-core:6.+")

    parserClasspath("org.springframework:spring-test:6.+")

    parserClasspath("org.springframework:spring-web:4.+")
    parserClasspath("org.springframework:spring-web:5.+")
    parserClasspath("org.springframework:spring-web:6.+")

    parserClasspath("org.springframework:spring-webflux:5.+")
    parserClasspath("org.springframework:spring-webmvc:5.+")

    parserClasspath("org.springframework.data:spring-data-commons:2.7.+")
    parserClasspath("org.springframework.data:spring-data-commons:1.+")
    parserClasspath("org.springframework.data:spring-data-jpa:2.+")
    parserClasspath("org.springframework.data:spring-data-jpa:2.3.+")
    parserClasspath("org.springframework.data:spring-data-mongodb:2.2.+")
    parserClasspath("org.mongodb:mongo-java-driver:3.12.+")

    parserClasspath("org.springframework.batch:spring-batch-core:4.+")
    parserClasspath("org.springframework.batch:spring-batch-core:5.1.+")
    parserClasspath("org.springframework.batch:spring-batch-infrastructure:5.1.+")

    parserClasspath("org.springframework:spring-messaging:5.+")
    parserClasspath("org.springframework.kafka:spring-kafka:2.9.+")
    parserClasspath("org.springframework.kafka:spring-kafka-test:2.9.+")
    parserClasspath("org.apache.kafka:kafka-clients:3.2.+")

    parserClasspath("org.springframework.security:spring-security-config:5.8.+")
    parserClasspath("org.springframework.security:spring-security-crypto:5.8.+")
    parserClasspath("org.springframework.security:spring-security-oauth2-client:5.8.+")
    parserClasspath("org.springframework.security:spring-security-web:5.8.+")

    parserClasspath("org.springframework.security:spring-security-config:6.0.+")
    parserClasspath("org.springframework.security:spring-security-core:6.0.+")
    parserClasspath("org.springframework.security:spring-security-web:6.0.+")

    parserClasspath("org.springframework.cloud:spring-cloud-sleuth-api:3.1.+")
//    parserClasspath("org.springframework.cloud:spring-cloud-sleuth-autoconfigure:3.1.+")
//    parserClasspath("org.springframework.cloud:spring-cloud-sleuth-instrumentation:3.1.+")
//    parserClasspath("org.springframework.cloud:spring-cloud-sleuth-brave:3.1.+")

    parserClasspath("com.nimbusds:nimbus-jose-jwt:9.13")
    parserClasspath("net.minidev:json-smart:2.4.+")

    parserClasspath("org.apache.httpcomponents.core5:httpcore5:5.1.+")
    parserClasspath("org.apache.httpcomponents.client5:httpclient5:5.1.+")

    parserClasspath("jakarta.servlet:jakarta.servlet-api:6.1.+")
    parserClasspath("io.micrometer:micrometer-commons:1.11.+")
    parserClasspath("io.micrometer:micrometer-core:1.11.+")
    parserClasspath("io.micrometer:micrometer-observation:1.11.+")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()

dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")
    implementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")

    runtimeOnly("org.openrewrite:rewrite-java-17")
    runtimeOnly("org.openrewrite.recipe:rewrite-apache:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-hibernate:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-micrometer:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-openapi:${rewriteVersion}")
    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.+")
    testRuntimeOnly(gradleApi())

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model:$rewriteVersion")

    // for generating properties migration configurations
    testImplementation("io.github.classgraph:classgraph:latest.release")
    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-kotlin:$rewriteVersion")
    testImplementation("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    testImplementation("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")

    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework:spring-web:4.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-starter-validation:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.hamcrest:hamcrest:2.2")
    "testWithSpringBoot_1_5RuntimeOnly"("junit:junit:latest.release")

    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework:spring-web:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework:spring-webmvc:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot:2.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.1.0.RELEASE")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.data:spring-data-jpa:2.1.0.RELEASE")
    "testWithSpringBoot_2_1RuntimeOnly"("javax.persistence:javax.persistence-api:2.2")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.security:spring-security-core:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.security:spring-security-config:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.security:spring-security-web:5.1.+")

    "testWithSpringBoot_2_2RuntimeOnly"("org.springframework.boot:spring-boot:2.2.+")
    "testWithSpringBoot_2_2RuntimeOnly"("org.springframework.boot:spring-boot-starter-validation:2.2.+")

    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-test:5.3.+")
    "testWithSpringBoot_2_3RuntimeOnly"("junit:junit:latest.release")
    "testWithSpringBoot_2_3RuntimeOnly"("org.hamcrest:hamcrest:2.2")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:2.3.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-web:5.2.+")
    "testWithSpringBoot_2_3Implementation"("org.springframework.data:spring-data-jpa:2.3.+")
    "testWithSpringBoot_2_3Implementation"("org.springframework.data:spring-data-mongodb:2.2.+")
    "testWithSpringBoot_2_3Implementation"("org.mongodb:mongo-java-driver:3.12.+")
    "testWithSpringBoot_2_3Implementation"("javax.persistence:javax.persistence-api:2.2")

    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.jooq:jooq:3.14.15")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-context:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-orm:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-web:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-webmvc:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-core:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-config:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-config:5.8.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-web:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-web:5.8.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-ldap:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-oauth2-client:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-oauth2-resource-server:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("jakarta.persistence:jakarta.persistence-api:2.2.3")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.data:spring-data-jpa:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.data:spring-data-jdbc:2.1.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-test:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-test:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-test-autoconfigure:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.batch:spring-batch-test:4.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("javax.servlet:javax.servlet-api:4.+")

    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.5.+")
    "testWithSpringBoot_2_5RuntimeOnly"("org.springframework:spring-web:5.3.+")

    "testWithSpringBoot_2_6RuntimeOnly"("org.springdoc:springdoc-openapi-common:1.+")
    "testWithSpringBoot_2_6RuntimeOnly"("io.swagger.core.v3:swagger-models:2.+")

    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework:spring-context:5.3.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot-starter:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework:spring-web:5.3.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework:spring-webmvc:5.3.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework:spring-webflux:5.3.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.data:spring-data-jpa:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.security:spring-security-core:5.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.security:spring-security-config:5.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.security:spring-security-web:5.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.security:spring-security-ldap:5.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")
    "testWithSpringBoot_2_7RuntimeOnly"("jakarta.persistence:jakarta.persistence-api:2.2.3")
    "testWithSpringBoot_2_7RuntimeOnly"("jakarta.validation:jakarta.validation-api:2.0.2")
    "testWithSpringBoot_2_7RuntimeOnly"("jakarta.xml.bind:jakarta.xml.bind-api:2.3.3")

    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.boot:spring-boot-starter:3.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.boot:spring-boot-starter-actuator:3.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:3.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework:spring-context:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework:spring-web:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework:spring-webmvc:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.batch:spring-batch-core:4.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.batch:spring-batch-core:5.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.batch:spring-batch-infrastructure:4.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.batch:spring-batch-infrastructure:5.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.security:spring-security-core:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.security:spring-security-config:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.security:spring-security-web:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.security:spring-security-ldap:6.0.+")
    "testWithSpringBoot_3_0RuntimeOnly"("jakarta.servlet:jakarta.servlet-api:6.1.+")

    "testWithSpringBoot_3_2RuntimeOnly"("org.springframework.boot:spring-boot-starter:3.2.+")
    "testWithSpringBoot_3_2RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:3.2.+")

    "testWithSpringBoot_3_4RuntimeOnly"("org.springframework.boot:spring-boot:3.4.+")
    "testWithSpringBoot_3_4RuntimeOnly"("org.springframework:spring-web:6.2.+")

    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework:spring-context:5.3.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.boot:spring-boot-starter:2.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.boot:spring-boot:2.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:2.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework:spring-web:5.3.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework:spring-webmvc:5.3.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework:spring-webflux:5.3.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.security:spring-security-core:5.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.security:spring-security-config:5.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.security:spring-security-web:5.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.springframework.security:spring-security-ldap:5.7.+")
    "testWithSpringSecurity_5_7RuntimeOnly"("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")

    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework:spring-context:5.3.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.boot:spring-boot-starter:2.7.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.boot:spring-boot:2.7.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:2.7.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework:spring-web:5.3.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework:spring-webmvc:5.3.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework:spring-webflux:5.3.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.security:spring-security-core:5.8.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.security:spring-security-config:5.8.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.security:spring-security-oauth2-client:5.8.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.security:spring-security-ldap:5.8.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.springframework.security:spring-security-web:5.8.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")
    "testWithSpringSecurity_5_8RuntimeOnly"("com.nimbusds:nimbus-jose-jwt:9.13")
    "testWithSpringSecurity_5_8RuntimeOnly"("net.minidev:json-smart")

    "testWithSpringSecurity_6_2RuntimeOnly"("org.springframework.security:spring-security-config:6.2.+")
    "testWithSpringSecurity_6_2RuntimeOnly"("org.springframework.security:spring-security-web:6.2.+")
}


sourceSetNames.forEach { sourceSet, versions ->
    versions.forEach { version ->
        val sourceSetName = "${sourceSet}${version}"
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
        tasks.check {
            dependsOn(testTask)
        }
    }
}

tasks {
    val generatePropertyMigrationRecipes by registering(JavaExec::class) {
        group = "generate"
        description = "Generate Spring Boot property migration recipes."
        mainClass = "org.openrewrite.java.spring.internal.GeneratePropertiesMigratorConfiguration"
        classpath = sourceSets.getByName("test").runtimeClasspath
    }
}
