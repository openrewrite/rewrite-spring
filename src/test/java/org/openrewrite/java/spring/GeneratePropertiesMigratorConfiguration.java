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
package org.openrewrite.java.spring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.spring.internal.SpringBootReleases;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/*
 * TODO move this to buildSrc or somewhere else where it can be run automatically
 */
public class GeneratePropertiesMigratorConfiguration {
    public static void main(String[] args) throws IOException {
        var springBootReleases = new SpringBootReleases(true);

        var objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        var releasesDir = new File(".boot-releases");
        //noinspection ResultOfMethodCallIgnored
        releasesDir.mkdirs();

        File[] listing = releasesDir.listFiles();
        Set<String> latestPatchReleases = Arrays.asList(args).contains("offline") ?
                (listing == null ? emptySet() :
                        Arrays.stream(listing).map(File::getName).collect(toSet())) :
                springBootReleases.latestPatchReleases();

        Files.createDirectories(Paths.get("build/rewrite/"));
        var config = Paths.get("build/rewrite/spring-boot-configuration-migration.yml");
        Files.write(config, ("\n" +
                             Files.readString(Paths.get("gradle/licenseHeader.txt")).replace("^", "# ") +
                             "\n").getBytes());

        var alreadyDefined = new HashSet<>();
        for (String version : latestPatchReleases) {
            if (!version.startsWith("2") && !version.startsWith("3")) {
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
                var replacements = scanResult.getResourcesWithLeafName("additional-spring-configuration-metadata.json").stream()
                        .flatMap(res -> {
                            try (InputStream inputStream = res.open()) {
                                var metadata = objectMapper.readValue(inputStream, SpringConfigurationMetadata.class);
                                return metadata.properties().stream()
                                        .filter(p -> p.deprecation() != null && p.deprecation().replacement() != null);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .filter(p -> alreadyDefined.add(p.name()))
                        .toList();

                if (!replacements.isEmpty()) {
                    var majorMinor = version.split("\\.");
                    Files.write(config, """
                                    ---
                                    type: specs.openrewrite.org/v1beta/recipe
                                    name: org.openrewrite.java.spring.boot2.SpringBootProperties_%s_%s_%s
                                    displayName: Migrate Spring Boot properties to %s.%s.%s
                                    description: Migrate properties found in `application.properties` and `application.yml`.
                                    recipeList:
                                    """.formatted(majorMinor[0], majorMinor[1], majorMinor[2], majorMinor[0], majorMinor[1], majorMinor[2]).getBytes(),
                            StandardOpenOption.APPEND);

                    Files.write(config, replacements.stream()
                                    .map(r -> """
                                              - org.openrewrite.java.spring.ChangeSpringPropertyKey:
                                                oldPropertyKey: %s
                                                newPropertyKey: %s
                                            """.formatted(
                                            r.name(), requireNonNull(r.deprecation()).replacement())
                                    )
                                    .collect(joining("", "\n", "\n"))
                                    .getBytes(),
                            StandardOpenOption.APPEND);
                }
            }
        }
    }
}

record SpringConfigurationMetadata(List<ConfigurationProperty> properties) {
}

record ConfigurationProperty(String name, @Nullable Deprecation deprecation) {
}

record Deprecation(@Nullable String replacement) {
}
