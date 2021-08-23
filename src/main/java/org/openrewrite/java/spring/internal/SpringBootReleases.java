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

import okhttp3.*;

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

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build();

    private final String repositoryUrl = "https://repo1.maven.org/maven2";

    public Stream<ModuleDownload> download(String version) {
        List<String> denyList = Arrays.asList("sample", "gradle", "experimental", "legacy",
                "maven", "tests", "spring-boot-versions");

        Request request = new Request.Builder()
                .url(repositoryUrl + "/org/springframework/boot")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            Set<String> modules = new HashSet<>();

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                Matcher moduleMatcher = Pattern.compile("href=\"([^\"]+)/\"")
                        .matcher(responseBody.string());

                while (moduleMatcher.find()) {
                    String module = moduleMatcher.group(1);
                    if (denyList.stream().noneMatch(module::contains)) {
                        modules.add(module);
                    }
                }

                return modules.stream()
                        .map(module -> {
                            Request moduleRequest = new Request.Builder()
                                    .url(repositoryUrl + "/org/springframework/boot/" + module + "/" + version +
                                            "/" + module + "-" + version + ".jar")
                                    .build();

                            try {
                                Response moduleResponse = httpClient.newCall(moduleRequest).execute();
                                if (!moduleResponse.isSuccessful()) {
                                    if (moduleResponse.code() == 404) {
                                        return null;
                                    }
                                    throw new IOException("Unexpected code " + moduleResponse);
                                }

                                ResponseBody moduleResponseBody = moduleResponse.body();
                                return moduleResponseBody == null ?
                                        null :
                                        new ModuleDownload(module, moduleResponseBody);
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
            Request request = new Request.Builder()
                    .url(repositoryUrl + "/org/springframework/boot/spring-boot-starter-parent")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                Set<String> releases = new HashSet<>();

                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    Matcher releaseMatcher = Pattern.compile("href=\"([^\"]+[.RELEASE]*)/\"")
                            .matcher(responseBody.string());

                    while (releaseMatcher.find()) {
                        if (releaseMatcher.group(1).equals("..")) {
                            continue;
                        }
                        releases.add(releaseMatcher.group(1));
                    }

                    availableReleases = releases;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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
        private final ResponseBody body;

        public ModuleDownload(String moduleName, ResponseBody body) {
            this.moduleName = moduleName;
            this.body = body;
        }

        public String getModuleName() {
            return moduleName;
        }

        public ResponseBody getBody() {
            return body;
        }
    }
}
