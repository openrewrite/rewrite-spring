/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.org.openrewrite.java.spring.boot2.search

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.cache.LocalMavenArtifactCache
import org.openrewrite.maven.cache.ReadOnlyLocalMavenArtifactCache
import org.openrewrite.maven.internal.MavenParsingException
import org.openrewrite.maven.utilities.MavenArtifactDownloader
import java.io.File
import java.nio.file.Paths
import java.util.function.Consumer

/**
 * @author Alex Boyko
 */
class IntegrationSchedulerPoolRecipeTest {

    @TempDir
    var testFolder: File? = null

    companion object {

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            val errorConsumer = Consumer<Throwable> { t ->
                if (t is MavenParsingException) {
                    println("  ${t.message}")
                } else {
                    t.printStackTrace()
                }
            }

            val downloader = MavenArtifactDownloader(
                ReadOnlyLocalMavenArtifactCache.mavenLocal().orElse(
                    LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".rewrite", "cache", "artifacts"))
                ),
                null,
                errorConsumer
            )

            val mavenParserBuilder = MavenParser.builder()
        }
    }
// TODO : Need to replace MavenProjectParser with a testing harness
//    @Test
//    fun wrongBootVersion() {
//
//        val pom = MavenParser.builder().build().parse(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.5.7</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """.trimIndent()).map{ it.withSourcePath(Paths.get("/project/pom.xml")) };
//
//        val props = PropertiesParser().parse("")
//            .map { it.withSourcePath(Paths.get("/project/src/main/resources/application.properties")) };
//
//        val results = IntegrationSchedulerPoolRecipe().run(pom + props);
//
//        assertThat(results.isEmpty()).isTrue();
//
//    }
//
//    @Test
//    fun noIntegrationDependency() {
//
//        val pom = MavenParser.builder().build().parse(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.4.13</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """.trimIndent()
//        ).map { it.withSourcePath(Paths.get("/project/pom.xml")) };
//
//        val props = PropertiesParser().parse("")
//            .map { it.withSourcePath(Paths.get("/project/src/main/resources/application.properties")) };
//
//        val results = IntegrationSchedulerPoolRecipe().run(pom + props)
//
//        assertThat(results.isEmpty()).isTrue();
//    }
//
//    @Test
//    fun noSchedulerProperty() {
//
//        val pom = File(testFolder, "pom.xml");
//        pom.writeText(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.4.13</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-integration</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """
//        );
//
//        val props = File(testFolder, "/src/main/resources/application.properties");
//        props.parentFile.mkdirs();
//        props.writeText("server.port=5674");
//
//        val java = File(testFolder, "/src/main/java/demo/Example.java");
//        java.parentFile.mkdirs();
//        java.writeText(
//            """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//            import org.springframework.context.annotation.Bean;
//
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            	@Bean
//                public String hello() {
//                    return "hello";
//                }
//
//            }
//          """.trimIndent()
//        );
//
//        val sources: List<Result> = Collections.emptyList()  //mavenProjectParser?.parse(pom.toPath().parent);
//
//        val results = IntegrationSchedulerPoolRecipe().run(sources)
//
//        assertThat(results.isEmpty()).isFalse();
//
//        assertThat(results.first().after.printAll()).isEqualTo(
//            """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//            import org.springframework.context.annotation.Bean;
//
//            // TODO: Scheduler thread pool size for Spring Integration either in properties or config server
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            	@Bean
//                public String hello() {
//                    return "hello";
//                }
//
//            }
//          """.trimIndent()
//        );
//
//    }
//
//    @Test
//    fun schedulerPropertyPresent() {
//
//        val pom = File(testFolder, "pom.xml");
//        pom.writeText(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.4.13</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-integration</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """
//        );
//
//        val props = File(testFolder, "/src/main/resources/application.properties");
//        props.parentFile.mkdirs();
//        props.writeText(
//            "server.port=6473\n" +
//                    "spring.task.scheduling.pool.size=4\n" +
//                    "spring.application.name=demo\n"
//        );
//
//        val java = File(testFolder, "/src/main/java/demo/Example.java");
//        java.parentFile.mkdirs();
//        java.writeText(
//            """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            }
//          """.trimIndent()
//        );
//
//        val sources = mavenProjectParser?.parse(pom.toPath().parent);
//
//        val results = IntegrationSchedulerPoolRecipe().run(sources)
//
//        assertThat(results.isEmpty()).isFalse();
//
//        assertThat(results.first().after.printAll()).isEqualTo(
//            "server.port=6473\n" +
//            "# TODO: Consider Scheduler thread pool size for Spring Integration\n" +
//            "spring.task.scheduling.pool.size=4\n" +
//            "spring.application.name=demo\n"
//        );
//
//    }
//
//    @Test
//    fun noSchedulerPropertyYaml() {
//
//        val pom = File(testFolder, "pom.xml");
//        pom.writeText(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.4.13</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <properties>
//                    <java.version>11</java.version>
//                </properties>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-integration</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """
//        );
//
//        val props = File(testFolder, "/src/main/resources/application.yml");
//        props.parentFile.mkdirs();
//        props.writeText(
//            """
//                server:
//                  port: 4563
//            """.trimIndent()
//        );
//
//        val java = File(testFolder, "/src/main/java/demo/Example.java");
//        java.parentFile.mkdirs();
//        java.writeText(
//          """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            }
//          """.trimIndent()
//        );
//
//        val sources = mavenProjectParser?.parse(pom.toPath().parent);
//
//        val results = IntegrationSchedulerPoolRecipe().run(sources)
//
//        assertThat(results.isEmpty()).isFalse();
//
//        assertThat(results.first().after.printAll()).isEqualTo(
//            """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//            // TODO: Scheduler thread pool size for Spring Integration either in properties or config server
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            }
//          """.trimIndent()
//        );
//
//    }
//
//    @Test
//    fun schedulerPropertyPresentYaml() {
//
//        val pom = File(testFolder, "pom.xml");
//        pom.writeText(
//            """<?xml version="1.0" encoding="UTF-8"?>
//            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
//                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
//                <modelVersion>4.0.0</modelVersion>
//                <parent>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-parent</artifactId>
//                    <version>2.4.13</version>
//                    <relativePath/> <!-- lookup parent from repository -->
//                </parent>
//                <groupId>com.example</groupId>
//                <artifactId>acme</artifactId>
//                <version>0.0.1-SNAPSHOT</version>
//                <properties>
//                    <java.version>11</java.version>
//                </properties>
//                <dependencies>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter</artifactId>
//                    </dependency>
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-integration</artifactId>
//                    </dependency>
//
//                    <dependency>
//                        <groupId>org.springframework.boot</groupId>
//                        <artifactId>spring-boot-starter-test</artifactId>
//                        <scope>test</scope>
//                    </dependency>
//                </dependencies>
//            </project>
//            """
//        );
//
//        val props = File(testFolder, "/src/main/resources/application.yml");
//        props.parentFile.mkdirs();
//        props.writeText(
//            """
//                server:
//                  port: 4563
//                spring:
//                  task:
//                    scheduling:
//                      foo: bar
//                      pool:
//                        size: 6
//                      bar: foo
//                  application:
//                    name: demo
//            """.trimIndent()
//        );
//
//        val java = File(testFolder, "/src/main/java/demo/Example.java");
//        java.parentFile.mkdirs();
//        java.writeText(
//            """
//            package demo;
//
//            import org.springframework.boot.SpringApplication;
//            import org.springframework.boot.autoconfigure.SpringBootApplication;
//
//            @SpringBootApplication
//            public class ExampleApplication {
//
//                public static void main(String[] args) {
//                    SpringApplication.run(ExampleApplication.class, args);
//                }
//
//            }
//          """.trimIndent()
//        );
//
////        val sources = mavenProjectParser?.parse(pom.toPath().parent);
//
//        val results = IntegrationSchedulerPoolRecipe().run(sources)
//
//        assertThat(results.isEmpty()).isFalse();
//
//        assertThat(results.first().after.printAll()).isEqualTo(
//            """
//                server:
//                  port: 4563
//                spring:
//                  task:
//                    scheduling:
//                      foo: bar
//                      pool:
//                # TODO: Consider Scheduler thread pool size for Spring Integration
//                        size: 6
//                      bar: foo
//                  application:
//                    name: demo
//            """.trimIndent()
//        );
//
//    }
}
