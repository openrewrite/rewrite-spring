plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

repositories {
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}

recipeDependencies {

    parserClasspath("javax.persistence:javax.persistence-api:2.+")
    parserClasspath("javax.validation:validation-api:2.0.1.Final")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:5.+")

    parserClasspath("org.springframework.boot:spring-boot:1.+")
    parserClasspath("org.springframework.boot:spring-boot:2.+")
    parserClasspath("org.springframework.boot:spring-boot:3.+")

    parserClasspath("org.springframework.boot:spring-boot-autoconfigure:2.+")
    parserClasspath("org.springframework.boot:spring-boot-autoconfigure:3.+")
    parserClasspath("org.springframework.boot:spring-boot-actuator:2.+")
    parserClasspath("org.springframework.boot:spring-boot-actuator:2.5.+")
    parserClasspath("org.springframework.boot:spring-boot-test:2.+")

    parserClasspath("org.springframework:spring-jdbc:4.1.+")

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

    parserClasspath("org.springdoc:springdoc-openapi-starter-common:2.+")

    parserClasspath("com.nimbusds:nimbus-jose-jwt:9.13")
    parserClasspath("net.minidev:json-smart:2.4.+")

    parserClasspath("org.apache.httpcomponents.core5:httpcore5:5.1.+")
    parserClasspath("org.apache.httpcomponents.client5:httpclient5:5.1.+")

    parserClasspath("jakarta.servlet:jakarta.servlet-api:6.1.+")
    parserClasspath("jakarta.validation:jakarta.validation-api:3.0.+")

    parserClasspath("io.micrometer:micrometer-commons:1.11.+")
    parserClasspath("io.micrometer:micrometer-core:1.11.+")
    parserClasspath("io.micrometer:micrometer-observation:1.11.+")

    testParserClasspath("com.nimbusds:nimbus-jose-jwt:9.13")
    testParserClasspath("io.projectreactor:reactor-core:3.6.3")
    testParserClasspath("io.springfox:springfox-core:3.+")
    testParserClasspath("io.springfox:springfox-spring-web:3.+")
    testParserClasspath("io.springfox:springfox-spi:3.+")
    testParserClasspath("io.springfox:springfox-bean-validators:3.+")
    testParserClasspath("io.swagger.core.v3:swagger-models:2.+")
    testParserClasspath("jakarta.persistence:jakarta.persistence-api:2.2.3")
    testParserClasspath("jakarta.validation:jakarta.validation-api:2.0.2")
    testParserClasspath("jakarta.validation:jakarta.validation-api:3.0.+")
    testParserClasspath("jakarta.xml.bind:jakarta.xml.bind-api:2.3.3")
    testParserClasspath("javax.persistence:javax.persistence-api:2.2")
    testParserClasspath("javax.servlet:javax.servlet-api:4.+")
    testParserClasspath("junit:junit:latest.release")
    testParserClasspath("org.apache.kafka:kafka-clients:3.2.3")
    testParserClasspath("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")
    testParserClasspath("org.hamcrest:hamcrest:2.2")
    testParserClasspath("org.jooq:jooq:3.14.15")
    testParserClasspath("org.jspecify:jspecify:1.0.0")
    testParserClasspath("org.mongodb:mongo-java-driver:3.12.+")
    testParserClasspath("org.springdoc:springdoc-openapi-common:1.+")
    testParserClasspath("org.hibernate.validator:hibernate-validator:6.0.23.Final")

    testParserClasspath("org.springframework.batch:spring-batch-core:5.+")
    testParserClasspath("org.springframework.batch:spring-batch-infrastructure:4.3.10")
    testParserClasspath("org.springframework.batch:spring-batch-infrastructure:5.+")
    testParserClasspath("org.springframework.batch:spring-batch-test:4.3.+")

    testParserClasspath("org.springframework.boot:spring-boot-actuator:2.4.+")
    testParserClasspath("org.springframework.boot:spring-boot-actuator:3.0.+")
    testParserClasspath("org.springframework.boot:spring-boot-actuator:3.4.+")
    testParserClasspath("org.springframework.boot:spring-boot-autoconfigure:1.5.+")
    testParserClasspath("org.springframework.boot:spring-boot-autoconfigure:2.3.+")
    testParserClasspath("org.springframework.boot:spring-boot-test-autoconfigure:2.4.+")
    testParserClasspath("org.springframework.boot:spring-boot-test:1.5.+")
    testParserClasspath("org.springframework.boot:spring-boot-test:2.4.+")
    testParserClasspath("org.springframework.boot:spring-boot-test:2.7.+")
    testParserClasspath("org.springframework.boot:spring-boot-test:3.0.+")
    testParserClasspath("org.springframework.boot:spring-boot-test:3.2.+")
    testParserClasspath("org.springframework.boot:spring-boot:1.5.+")
    testParserClasspath("org.springframework.boot:spring-boot:2.1.+")
    testParserClasspath("org.springframework.boot:spring-boot:2.2.+")
    testParserClasspath("org.springframework.boot:spring-boot:2.4.+")
    testParserClasspath("org.springframework.boot:spring-boot:2.7.+")
    testParserClasspath("org.springframework.boot:spring-boot:3.4.+")

    testParserClasspath("org.springframework.cloud:spring-cloud-openfeign-core:4.1.0")

    testParserClasspath("org.springframework.data:spring-data-jdbc:2.1.+")
    testParserClasspath("org.springframework.data:spring-data-jpa:2.1.0.RELEASE")
    testParserClasspath("org.springframework.data:spring-data-jpa:2.3.+")
    testParserClasspath("org.springframework.data:spring-data-jpa:2.4.+")
    testParserClasspath("org.springframework.data:spring-data-jpa:2.7.+")
    testParserClasspath("org.springframework.data:spring-data-jpa:3.4.7")
    testParserClasspath("org.springframework.data:spring-data-mongodb:2.2.+")

    testParserClasspath("org.springframework.plugin:spring-plugin-core:2.0.0.RELEASE")

    testParserClasspath("org.springframework.security:spring-security-config:5.1.+")
    testParserClasspath("org.springframework.security:spring-security-config:5.5.+")
    testParserClasspath("org.springframework.security:spring-security-config:5.7.+")
    testParserClasspath("org.springframework.security:spring-security-config:6.2.+")
    testParserClasspath("org.springframework.security:spring-security-core:5.1.+")
    testParserClasspath("org.springframework.security:spring-security-core:5.5.+")
    testParserClasspath("org.springframework.security:spring-security-core:5.7.+")
    testParserClasspath("org.springframework.security:spring-security-core:5.8.+")
    testParserClasspath("org.springframework.security:spring-security-web:5.1.+")
    testParserClasspath("org.springframework.security:spring-security-web:5.5.+")
    testParserClasspath("org.springframework.security:spring-security-web:5.7.+")
    testParserClasspath("org.springframework.security:spring-security-web:6.2.+")

    testParserClasspath("org.springframework:spring-context:6.0.+")
    testParserClasspath("org.springframework:spring-context:6.2.+")
    testParserClasspath("org.springframework:spring-orm:5.3.+")
    testParserClasspath("org.springframework:spring-test:5.3.+")
    testParserClasspath("org.springframework:spring-tx:4.1.+")
    testParserClasspath("org.springframework:spring-web:5.1.+")
    testParserClasspath("org.springframework:spring-web:5.2.+")
    testParserClasspath("org.springframework:spring-web:6.0.+")
    testParserClasspath("org.springframework:spring-web:6.1.8")
    testParserClasspath("org.springframework:spring-webflux:6.1.16")
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

    runtimeOnly("org.openrewrite:rewrite-java-21")
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
    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-kotlin")
    testImplementation("org.openrewrite.recipe:rewrite-migrate-java:$rewriteVersion")
    testImplementation("org.openrewrite.recipe:rewrite-testing-frameworks:$rewriteVersion")

    // Needed for `org.openrewrite.java.spring.http.ReplaceLiteralsTest` to read constant values
    testRuntimeOnly("org.springframework:spring-web:6.+")
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        forkEvery = 0
    }
    val generatePropertyMigrationRecipes by registering(JavaExec::class) {
        group = "generate"
        description = "Generate Spring Boot property migration recipes."
        mainClass = "org.openrewrite.java.spring.internal.GeneratePropertiesMigratorConfiguration"
        classpath = sourceSets.getByName("test").runtimeClasspath
    }
}
