package org.openrewrite.gradle.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.test.RewriteTest;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.gradle.Assertions.buildGradle;

class AddSpringDependencyManagementPluginTest implements RewriteTest {
    @Test
    void addIfPresent() {
        GradleProject gp = gradleProject(springBootPlugin(), springDependencyManagementPlugin());
        rewriteRun(
          spec -> spec.allSources(source -> source.markers(gp))
            .recipe(new AddSpringDependencyManagementPlugin()),
          buildGradle(
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "1.5.22.RELEASE"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  compile "org.springframework.boot:spring-boot-starter-web"
              }
              """,
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "1.5.22.RELEASE"
                  id "io.spring.dependency-management" version "1.0.6.RELEASE"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  compile "org.springframework.boot:spring-boot-starter-web"
              }
              """
          )
        );
    }

    @Test
    void dontAddIfNotUsing() {
        GradleProject gp = gradleProject(springBootPlugin());
        rewriteRun(
          spec -> spec.allSources(source -> source.markers(gp))
            .recipe(new AddSpringDependencyManagementPlugin()),
          buildGradle(
            """
              plugins {
                  id "java"
                  id "org.springframework.boot" version "2.6.15"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation platform("org.springframework.boot:spring-boot-starter-dependencies:2.6.15")
                  implementation "org.springframework.boot:spring-boot-starter-web"
              }
              """
          )
        );
    }

    private static GradleProject gradleProject(GradlePluginDescriptor... gradlePlugins) {
        return new GradleProject(
          Tree.randomId(),
          "example",
          ":",
          List.of(gradlePlugins),
          Collections.emptyList(),
          Collections.singletonList(MavenRepository.builder()
            .id("Gradle Central Plugin Repository")
            .uri("https://plugins.gradle.org/m2")
            .releases(true)
            .snapshots(true)
            .build()),
          Collections.emptyMap()
        );
    }

    private static GradlePluginDescriptor springBootPlugin() {
        return new GradlePluginDescriptor("org.springframework.boot.gradle.plugin.SpringBootPlugin", "org.springframework.boot");
    }

    private static GradlePluginDescriptor springDependencyManagementPlugin() {
        return new GradlePluginDescriptor("io.spring.dependencymanagement.DependencyManagementPlugin", "io.spring.dependency-management");
    }
}
