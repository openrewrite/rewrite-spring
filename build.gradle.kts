plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Eliminate legacy Spring patterns and migrate between major Spring Boot versions. Automatically."

val springBootVersions: List<String> = listOf("1_5", "2_1", "2_2", "2_3", "2_4", "2_5", "2_6", "2_7", "3_0")

sourceSets {
    springBootVersions.forEach { version ->
        create("testWithSpringBoot_${version}") {
            java {
                compileClasspath += sourceSets.getByName("main").output
                runtimeClasspath += sourceSets.getByName("main").output
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
    springBootVersions.forEach { version ->
        getByName("testWithSpringBoot_${version}RuntimeOnly") {
            isCanBeResolved = true
            extendsFrom(getByName("testRuntimeOnly"))
        }
        getByName("testWithSpringBoot_${version}Implementation") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
    }
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
var springBoot3Version = "3.0.0-RC1"
dependencies {
    implementation("org.openrewrite:rewrite-java:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-xml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-properties:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-yaml:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-maven:${rewriteVersion}")

    runtimeOnly("org.openrewrite:rewrite-java-17:$rewriteVersion")
    runtimeOnly("org.openrewrite.recipe:rewrite-testing-frameworks:${rewriteVersion}")
    runtimeOnly("org.openrewrite.recipe:rewrite-migrate-java:${rewriteVersion}")
    runtimeOnly("org.openrewrite:rewrite-java-17:$rewriteVersion")

    testImplementation("com.github.marschall:memoryfilesystem:latest.release")

    // for generating properties migration configurations
    testImplementation("io.github.classgraph:classgraph:latest.release")
    testImplementation("org.openrewrite:rewrite-java-17:${rewriteVersion}")
    testImplementation("org.openrewrite.recipe:rewrite-migrate-java:${rewriteVersion}")
    testImplementation("org.openrewrite.recipe:rewrite-testing-frameworks:${rewriteVersion}")

    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework:spring-web:4.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")
    "testWithSpringBoot_1_5RuntimeOnly"("org.hamcrest:hamcrest:2.2")
    "testWithSpringBoot_1_5RuntimeOnly"("junit:junit:latest.release")

    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework:spring-web:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework:spring-webmvc:5.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot:2.1.+")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.1.0.RELEASE")
    "testWithSpringBoot_2_1RuntimeOnly"("org.springframework.data:spring-data-jpa:2.1.0.RELEASE")
    "testWithSpringBoot_2_1RuntimeOnly"("javax.persistence:javax.persistence-api:2.2")

    "testWithSpringBoot_2_2RuntimeOnly"("org.springframework.boot:spring-boot:2.2.+")

    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-test:5.3.+")
    "testWithSpringBoot_2_3RuntimeOnly"("junit:junit:latest.release")
    "testWithSpringBoot_2_3RuntimeOnly"("org.hamcrest:hamcrest:2.2")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-test:1.5.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.boot:spring-boot-autoconfigure:2.3.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework:spring-web:5.2.+")
    "testWithSpringBoot_2_3RuntimeOnly"("org.springframework.data:spring-data-jpa:2.3.0.RELEASE")
    "testWithSpringBoot_2_3RuntimeOnly"("javax.persistence:javax.persistence-api:2.2")

    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-actuator:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.jooq:jooq:3.14.15")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-context:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-orm:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-web:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-webmvc:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-core:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-config:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-web:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.security:spring-security-ldap:5.5.+")
    "testWithSpringBoot_2_4RuntimeOnly"("jakarta.persistence:jakarta.persistence-api:2.2.3")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.data:spring-data-jpa:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.data:spring-data-jdbc:2.1.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework:spring-test:5.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-test:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.boot:spring-boot-test-autoconfigure:2.4.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.apache.tomcat.embed:tomcat-embed-core:9.0.+")
    "testWithSpringBoot_2_4RuntimeOnly"("org.springframework.batch:spring-batch-test:4.3.+")
    "testWithSpringBoot_2_4RuntimeOnly"("javax.servlet:javax.servlet-api:4.+")

    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot-starter:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:2.7.+")
    "testWithSpringBoot_2_7RuntimeOnly"("org.springframework:spring-context:5.3.+")

    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.boot:spring-boot-starter:${springBoot3Version}")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework.boot:spring-boot-starter-test:${springBoot3Version}")
    "testWithSpringBoot_3_0RuntimeOnly"("org.springframework:spring-context:6.0.+")

}

springBootVersions.forEach { version ->
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
    tasks.check {
        dependsOn(testTask)
    }
}
