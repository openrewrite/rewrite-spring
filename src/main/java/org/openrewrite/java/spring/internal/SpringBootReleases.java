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


import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.ipc.http.HttpUrlConnectionSender;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Stream.empty;

public class SpringBootReleases {
    private static volatile Set<String> availableReleases;

    private final String repositoryUrl = "https://repo1.maven.org/maven2";

    private final String milestoneRepositoryUrl = "https://repo.spring.io/milestone";

    private final boolean includeReleaseCandidates;

    public SpringBootReleases(boolean includeReleaseCandidates) {
        this.includeReleaseCandidates = includeReleaseCandidates;
    }
    public Stream<ModuleDownload> download(String version) {
        List<String> denyList = Arrays.asList("sample", "gradle", "experimental", "legacy",
                "maven", "tests", "spring-boot-versions");
        HttpUrlConnectionSender httpSender = new HttpUrlConnectionSender();

        HttpSender.Request request = HttpSender.Request.build((version.contains("-RC") ? milestoneRepositoryUrl :  repositoryUrl) + "/org/springframework/boot", httpSender)
                .withMethod(HttpSender.Method.GET)
                .build();

        try (HttpSender.Response response = httpSender.send(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            Set<String> modules = new HashSet<>();

            if (response.isSuccessful()) {
                Matcher moduleMatcher = Pattern.compile("href=\"([^\"]+)/\"")
                        .matcher(new String(response.getBodyAsBytes()));

                while (moduleMatcher.find()) {
                    String module = moduleMatcher.group(1);
                    if (denyList.stream().noneMatch(module::contains)) {
                        modules.add(module);
                    }
                }

                return modules.stream()
                        .map(module -> {
                            HttpSender.Request moduleRequest = HttpSender.Request
                                    .build((version.contains("-RC") ? milestoneRepositoryUrl :  repositoryUrl) + "/org/springframework/boot/" + module + "/" + version +
                                            "/" + module + "-" + version + ".jar", httpSender)
                                    .withMethod(HttpSender.Method.GET)
                                    .build();

                            try(HttpSender.Response moduleResponse = httpSender.send(moduleRequest)) {
                                if (!moduleResponse.isSuccessful()) {
                                    if (moduleResponse.getCode() == 404) {
                                        return null;
                                    }
                                    throw new IOException("Unexpected code " + moduleResponse);
                                }
                                byte[] body = moduleResponse.getBodyAsBytes();
                                if(body.length == 0) {
                                    return null;
                                }
                                return new ModuleDownload(module, body);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .filter(Objects::nonNull);
            }

            return empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Set<String> allReleases() {
        if (availableReleases == null) {
            HttpUrlConnectionSender httpSender = new HttpUrlConnectionSender();
            HttpSender.Request request = HttpSender.Request
                    .build(repositoryUrl + "/org/springframework/boot/spring-boot-starter-parent", httpSender)
                    .withMethod(HttpSender.Method.GET)
                    .build();

            Set<String> releases = new HashSet<>();

            try (HttpSender.Response response = httpSender.send(request)) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                byte[] responseBody = response.getBodyAsBytes();
                if (responseBody.length > 0) {
                    Matcher releaseMatcher = Pattern.compile("href=\"([^\"]+[.RELEASE]*)/\"")
                            .matcher(new String(responseBody));

                    while (releaseMatcher.find()) {
                        if ("..".equals(releaseMatcher.group(1))) {
                            continue;
                        }
                        releases.add(releaseMatcher.group(1));
                    }
                }

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            if (includeReleaseCandidates) {
                request = HttpSender.Request
                        .build(milestoneRepositoryUrl + "/org/springframework/boot/spring-boot-starter-parent", httpSender)
                        .withMethod(HttpSender.Method.GET)
                        .build();

                try (HttpSender.Response response = httpSender.send(request)) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    byte[] responseBody = response.getBodyAsBytes();
                    if (responseBody.length > 0) {
                        Matcher releaseMatcher = Pattern.compile("href=\"([^\"]+-RC[0-9]]*)/\"")
                                .matcher(new String(responseBody));

                        while (releaseMatcher.find()) {
                            if ("..".equals(releaseMatcher.group(1))) {
                                continue;
                            }
                            releases.add(releaseMatcher.group(1));
                        }
                    }

                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

            }
            availableReleases = releases;

        }

        return availableReleases;
    }

    /**
     * @return The set of latest patch releases for each available minor release.
     */
    public Set<String> latestPatchReleases() {
        return allReleases().stream()
                .collect(Collectors.groupingBy(v -> {
                    String[] versionParts = v.split("\\.");
                    return versionParts[0] + "." + versionParts[1];
                }))
                .values()
                .stream()
                .map(patches -> patches.stream().max((r1, r2) -> {
                            String[] r1Parts = r1.split("[\\.-]");
                            String[] r2Parts = r2.split("[\\.-]");

                            int majorVersionComp = r1Parts[0].compareTo(r2Parts[0]);
                            if (majorVersionComp != 0) {
                                return majorVersionComp;
                            }

                            int minorVersionComp = Integer.parseInt(r1Parts[1]) - Integer.parseInt(r2Parts[1]);
                            if (minorVersionComp != 0) {
                                return minorVersionComp;
                            }

                            int patchVersionComp = Integer.parseInt(r1Parts[2]) - Integer.parseInt(r2Parts[2]);
                            if (patchVersionComp != 0) {
                                return patchVersionComp;
                            }

                            // should never get to this point
                            return r1.compareTo(r2);
                        }).orElseThrow(() -> new IllegalStateException("Patch list should not be empty"))
                )
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String latestMatchingVersion(String version) {
        if (!version.contains("+")) {
            return version;
        }

        return allReleases().stream()
                .filter(release -> release.matches(version.replace("+", ".+")))
                .max((r1, r2) -> {
                    String[] r1Parts = r1.split("\\.");
                    String[] r2Parts = r2.split("\\.");

                    int majorVersionComp = r1Parts[0].compareTo(r2Parts[0]);
                    if (majorVersionComp != 0) {
                        return majorVersionComp;
                    }

                    int minorVersionComp = Integer.parseInt(r1Parts[1]) - Integer.parseInt(r2Parts[1]);
                    if (minorVersionComp != 0) {
                        return minorVersionComp;
                    }

                    int patchVersionComp = Integer.parseInt(r1Parts[2]) - Integer.parseInt(r2Parts[2]);
                    if (patchVersionComp != 0) {
                        return patchVersionComp;
                    }

                    // should never get to this point
                    return r1.compareTo(r2);
                })
                .orElse(null);
    }

    public static class ModuleDownload {
        private final String moduleName;
        private final byte[] body;

        public ModuleDownload(String moduleName, byte[] body) {
            this.moduleName = moduleName;
            this.body = body;
        }

        public String getModuleName() {
            return moduleName;
        }

        public byte[] getBody() {
            return body;
        }
    }
}
