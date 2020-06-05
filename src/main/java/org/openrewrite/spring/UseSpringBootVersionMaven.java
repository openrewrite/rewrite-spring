/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import okhttp3.*;
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.ChangeTagValue;
import org.openrewrite.xml.XmlRefactorVisitor;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update the parent section of a POM to a version specified to this visitor.
 */
public class UseSpringBootVersionMaven extends XmlRefactorVisitor {
    private static volatile Set<String> availableReleases;

    private final XPathMatcher parentVersion = new XPathMatcher("/project/parent/version");

    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build();

    /**
     * 2.3.0.RELEASE, 2.3.+, 2.+
     */
    private String requestedVersion;

    private String repositoryUrl = "https://repo1.maven.org/maven2";

    //VisibleForTesting
    @Nullable
    String latestMatchingVersion;

    public UseSpringBootVersionMaven() {
        setCursoringOn();
    }

    @Override
    public Iterable<Tag> getTags() {
        return Tags.of("version.requested", requestedVersion, "version", latestMatchingVersion);
    }

    public void setVersion(String version) {
        this.requestedVersion = version;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Validated validate() {
        return super.validate();
    }

    @Override
    public Xml visitDocument(Xml.Document document) {
        this.latestMatchingVersion = latestMatchingVersion(requestedVersion, httpClient, repositoryUrl);
        return super.visitDocument(document);
    }

    @Override
    public Xml visitTag(Xml.Tag tag) {
        if (parentVersion.matches(getCursor()) &&
                tag.getSibling("groupId", getCursor())
                        .flatMap(Xml.Tag::getValue)
                        .map(groupId -> groupId.equals("org.springframework.boot"))
                        .orElse(false) &&
                tag.getSibling("artifactId", getCursor())
                        .flatMap(Xml.Tag::getValue)
                        .map(artifactId -> artifactId.equals("spring-boot-starter-parent"))
                        .orElse(false)) {
            if (latestMatchingVersion != null && tag.getValue().map(v -> !v.equals(latestMatchingVersion))
                    .orElse(true)) {
                andThen(new ChangeTagValue.Scoped(tag, latestMatchingVersion));
            }
        }

        return super.visitTag(tag);
    }

    //VisibleForTesting
    static String latestMatchingVersion(String version, OkHttpClient httpClient, String repositoryUrl) {
        if (!version.contains("+")) {
            return version;
        }

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
                    Matcher releaseMatcher = Pattern.compile("href=\"([^\"]+.RELEASE)/\"")
                            .matcher(responseBody.string());

                    while (releaseMatcher.find()) {
                        releases.add(releaseMatcher.group(1));
                    }

                    availableReleases = releases;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return availableReleases.stream()
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
}
