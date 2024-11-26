/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.maven.tree.Version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;

class GeneratePropertiesMigratorConfiguration {
    private static final ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static void main(String[] args) throws IOException {
        var springBootReleases = new SpringBootReleases(true); // `true` for release candidates
        var releasesDir = new File(".boot-releases");
        //noinspection ResultOfMethodCallIgnored
        releasesDir.mkdirs();

        File[] listing = releasesDir.listFiles();
        Set<String> latestPatchReleases = Arrays.asList(args).contains("offline") ?
          (listing == null ? emptySet() :
            Arrays.stream(listing).map(File::getName).collect(toSet())) :
          springBootReleases.latestPatchReleases();

        var alreadyDefined = new HashSet<>();
        for (String version : latestPatchReleases) {
            Version semanticVersion = new Version(version);
            // We only need to scan one outdated version to prevent duplicate migration recipes
            if (semanticVersion.compareTo(new Version("3.0")) < 0) {
                continue;
            }
            var versionDir = new File(releasesDir, version);
            if (versionDir.mkdirs()) {
                System.out.println("Downloading version " + version);
                springBootReleases.download(version).forEach(download -> {
                    try {
                        Files.write(versionDir.toPath().resolve(download.getModuleName() + "-" +
                          version + ".jar"), download.getBody());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } else {
                System.out.println("Using existing download of version " + version);
            }

            System.out.println("Scanning version " + version);
            try (ScanResult scanResult = new ClassGraph()
              .overrideClasspath(Arrays.stream(requireNonNull(versionDir.listFiles())).map(File::toURI).collect(Collectors.toList()))
              .acceptPaths("META-INF")
              .enableMemoryMapping()
              .scan()) {
                List<SpringConfigurationMetadata.ConfigurationProperty> deprecations = getDeprecations(scanResult, alreadyDefined);
                if (deprecations.isEmpty()) {
                    continue;
                }
                var majorMinor = version.split("\\.");
                if (semanticVersion.compareTo(new Version("3.1")) < 0) {
                    // Don't override manual fixes to the unsupported 2.x and 3.0 versions anymore
                    continue;
                }
                var recipePath = Paths.get("src/main/resources/META-INF/rewrite/spring-boot-%s%s-properties.yml".formatted(majorMinor[0], majorMinor[1]));
                writeFileHeader(majorMinor, recipePath);
                generateReplacementRecipes(deprecations, recipePath);
                generateCommentRecipesForDeprecations(deprecations, recipePath);
            }
        }
    }

    private static @NotNull List<SpringConfigurationMetadata.ConfigurationProperty> getDeprecations(ScanResult scanResult, HashSet<Object> alreadyDefined) {
        ResourceList resources = scanResult.getResourcesMatchingWildcard("**/*spring-configuration-metadata.json");
        return resources.stream()
          .flatMap(res -> {
              try (InputStream inputStream = res.open()) {
                  var metadata = objectMapper.readValue(inputStream, SpringConfigurationMetadata.class);
                  return metadata.properties().stream()
                    .filter(p -> p.deprecation() != null)
                    .filter(p -> alreadyDefined.add(getPropertyKey(p)));
              } catch (IOException e) {
                  throw new UncheckedIOException(e);
              }
          })
          .toList();
    }

    private static void generateReplacementRecipes(List<SpringConfigurationMetadata.ConfigurationProperty> properties, Path recipePath) throws IOException {
        var replacements = properties.stream()
          .filter(d -> (d.deprecation() != null) && d.deprecation().replacement() != null)
          .sorted(Comparator.comparing(SpringConfigurationMetadata.ConfigurationProperty::name))
          .toList();

        if (!replacements.isEmpty()) {
            Files.writeString(recipePath, replacements.stream()
                .map(r -> """
                    - org.openrewrite.java.spring.ChangeSpringPropertyKey:
                        oldPropertyKey: %s
                        newPropertyKey: %s
                  """.formatted(
                  r.name(), requireNonNull(r.deprecation()).replacement())
                )
                .collect(joining("", "\n", "\n")),
              StandardOpenOption.APPEND);
        }
    }

    private static void generateCommentRecipesForDeprecations(List<SpringConfigurationMetadata.ConfigurationProperty> properties, Path recipePath) throws IOException {
        var deprecationsWithoutReplacement = properties.stream()
          .filter(d -> (d.deprecation() != null) && d.deprecation().replacement() == null)
          .sorted(Comparator.comparing(SpringConfigurationMetadata.ConfigurationProperty::name))
          .toList();

        if (!deprecationsWithoutReplacement.isEmpty()) {
            Files.writeString(recipePath, """
                      - org.openrewrite.java.spring.InlineCommentSpringProperties:
                          comment: "This property is deprecated and will be removed in future Spring Boot versions"
                          propertyKeys:
                    """,
              StandardOpenOption.APPEND);

              Files.writeString(recipePath, deprecationsWithoutReplacement.stream()
                .map(r -> """
                          - %s
                  """.formatted(
                  r.name())
                )
                .collect(joining("", "", "\n")),
              StandardOpenOption.APPEND);
        }
    }

    private static String getPropertyKey(SpringConfigurationMetadata.ConfigurationProperty property) {
        String replacementSuffix = (property.deprecation() != null) && (property.deprecation().replacement() != null)? "->" + property.deprecation().replacement() : "";
        return property.name() + replacementSuffix;
    }

    private static void writeFileHeader(String[] majorMinor, Path recipePath) throws IOException {
        Files.writeString(recipePath, "#\n" +
          Files.readAllLines(Paths.get("gradle/licenseHeader.txt"))
            .stream()
            .map(str -> str.replaceAll("^", "# "))
            .map(str -> str.replace("${year}", LocalDate.now().getYear() + ""))
            .collect(Collectors.joining("\n")) + "\n#\n");

        Files.writeString(recipePath, """
            # This file is automatically generated by the GeneratePropertiesMigratorConfiguration class.
            # Do not edit this file manually. Update the Spring Boot property metadata upstream instead.
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: org.openrewrite.java.spring.boot%1$s.SpringBootProperties_%1$s_%2$s
            displayName: Migrate Spring Boot properties to %1$s.%2$s
            description: Migrate properties found in `application.properties` and `application.yml`.
            tags:
              - spring
              - boot
            recipeList:""".formatted(majorMinor[0], majorMinor[1]),
          StandardOpenOption.APPEND);
    }
}

record SpringConfigurationMetadata(List<ConfigurationProperty> properties) {
    record ConfigurationProperty(String name, @Nullable Deprecation deprecation) {
        record Deprecation(@Nullable String replacement) {
        }
    }
}
