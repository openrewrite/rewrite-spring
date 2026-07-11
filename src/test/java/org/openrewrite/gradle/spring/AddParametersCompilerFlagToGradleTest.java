/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;

class AddParametersCompilerFlagToGradleTest implements RewriteTest {

    private static GradleProject gradleProject(GradlePluginDescriptor... plugins) {
        return GradleProject.builder()
          .group("com.example")
          .name("demo")
          .version("1.0")
          .path(":")
          .plugins(List.of(plugins))
          .mavenRepositories(List.of(MavenRepository.builder()
            .id("Central")
            .uri("https://repo.maven.apache.org/maven2")
            .releases(true)
            .snapshots(false)
            .build()))
          .build();
    }

    private static GradlePluginDescriptor javaPlugin() {
        return new GradlePluginDescriptor("org.gradle.api.plugins.JavaPlugin", "java");
    }

    private static GradlePluginDescriptor kotlinJvmPlugin() {
        return new GradlePluginDescriptor(
          "org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper",
          "org.jetbrains.kotlin.jvm");
    }

    private static GradlePluginDescriptor springBootPlugin() {
        return new GradlePluginDescriptor(
          "org.springframework.boot.gradle.plugin.SpringBootPlugin",
          "org.springframework.boot");
    }

    @DocumentExample
    @Test
    void addsJavaCompileBlockGroovy() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              """,
            """
              plugins {
                  id 'java'
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              """
          )
        );
    }

    @Test
    void addsJavaAndKotlinCompileBlockGroovy() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(), kotlinJvmPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }
              """,
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                  compilerOptions.javaParameters = true
              }
              """
          )
        );
    }

    @Test
    void addsKotlinBlockWhenPluginIdentifiedByClassNameOnly() {
        // Plugins applied via the buildscript classpath (apply plugin: SomeClass) have no id in the marker
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(),
              new GradlePluginDescriptor("org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper", null)))),
          //language=groovy
          buildGradle(
            """
              apply plugin: org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
              """,
            """
              apply plugin: org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                  compilerOptions.javaParameters = true
              }
              """
          )
        );
    }

    @Test
    void addsJavaFlagWhenOnlyKotlinFlagAlreadyPresent() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(), kotlinJvmPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }

              tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                  kotlinOptions.freeCompilerArgs += ['-java-parameters']
              }
              """,
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }

              tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                  kotlinOptions.freeCompilerArgs += ['-java-parameters']
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              """
          )
        );
    }

    @Test
    void addsKotlinFlagWhenOnlyJavaFlagAlreadyPresent() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(), kotlinJvmPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              """,
            """
              plugins {
                  id 'org.jetbrains.kotlin.jvm' version '1.9.25'
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }

              tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                  compilerOptions.javaParameters = true
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenParametersFlagAlreadyPresentGroovy() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              tasks.withType(JavaCompile).configureEach {
                  options.compilerArgs.add('-parameters')
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenSpringBootPluginApplied() {
        // The Spring Boot Gradle plugin already configures -parameters on JavaCompile tasks
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(), springBootPlugin()))),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'org.springframework.boot' version '3.3.0'
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenJavaPluginNotApplied() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject())),
          //language=groovy
          buildGradle(
            """
              subprojects {
                  repositories {
                      mavenCentral()
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithoutGradleProjectMarker() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              """
          )
        );
    }

    @Test
    void addsJavaCompileBlockKotlinDsl() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin()))),
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  java
              }
              """,
            """
              plugins {
                  java
              }

              tasks.withType<JavaCompile>().configureEach {
                  options.compilerArgs.add("-parameters")
              }
              """
          )
        );
    }

    @Test
    void addsJavaAndKotlinCompileBlockKotlinDsl() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin(), kotlinJvmPlugin()))),
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  kotlin("jvm") version "1.9.25"
              }
              """,
            """
              plugins {
                  kotlin("jvm") version "1.9.25"
              }

              tasks.withType<JavaCompile>().configureEach {
                  options.compilerArgs.add("-parameters")
              }
              tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                  compilerOptions.javaParameters.set(true)
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenParametersFlagAlreadyPresentKotlinDsl() {
        rewriteRun(
          spec -> spec.recipe(new AddParametersCompilerFlagToGradle())
            .allSources(source -> source.markers(gradleProject(javaPlugin()))),
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  java
              }

              tasks.withType<JavaCompile>().configureEach {
                  options.compilerArgs.add("-parameters")
              }
              """
          )
        );
    }
}
