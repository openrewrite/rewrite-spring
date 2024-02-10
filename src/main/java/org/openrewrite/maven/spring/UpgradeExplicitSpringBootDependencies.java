/*
 * Copyright 2021 - 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openrewrite.maven.spring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import org.openrewrite.*;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.*;
import org.openrewrite.semver.XRange;
import org.openrewrite.xml.tree.Xml;

import java.util.*;

import static java.util.Collections.emptyMap;

@EqualsAndHashCode(callSuper = false)
public class UpgradeExplicitSpringBootDependencies extends Recipe {

    private static final String SPRINGBOOT_GROUP = "org.springframework.boot";
    private static final String SPRING_BOOT_DEPENDENCIES = "spring-boot-dependencies";

    private transient final Map<String, String> springBootDependenciesMap = new HashMap<>();

    @Option(displayName = "From Spring Version",
            description = "XRage pattern for spring version used to limit which projects should be updated",
            example = " 2.7.+")
    private final String fromVersion;

    @Option(displayName = "To Spring Version",
            description = "Upgrade version of `org.springframework.boot`",
            example = "3.0.0-M3")
    private final String toVersion;

    @JsonCreator
    public UpgradeExplicitSpringBootDependencies(@JsonProperty("fromVersion") String fromVersion, @JsonProperty("toVersion") String toVersion) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }

    @Override
    public String getDisplayName() {
        return "Upgrade un-managed spring project dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrades un-managed spring-boot project dependencies according to the specified spring-boot version.";
    }

    private TreeVisitor<?, ExecutionContext> precondition() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag resultTag = super.visitTag(tag, ctx);
                if (isManagedDependencyTag()) {
                    ResolvedManagedDependency managedDependency = findManagedDependency(resultTag);
                    if (managedDependency != null && managedDependency.getGroupId().equals(SPRINGBOOT_GROUP)
                        && satisfiesOldVersionPattern(managedDependency.getVersion())) {
                        return applyThisRecipe(resultTag);
                    }
                }

                if (isDependencyTag()) {
                    ResolvedDependency dependency = findDependency(resultTag);
                    if ((dependency != null) && dependency.getGroupId().equals(SPRINGBOOT_GROUP)
                        && satisfiesOldVersionPattern(dependency.getVersion())) {
                        return applyThisRecipe(resultTag);
                    }
                }
                return resultTag;
            }

            @NonNull
            private Xml.Tag applyThisRecipe(Xml.Tag resultTag) {
                return resultTag.withMarkers(resultTag.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), "SpringBoot dependency")));
            }

            private boolean satisfiesOldVersionPattern(@Nullable String version) {
                return version != null && XRange.build(fromVersion, version).isValid();
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(precondition(), new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                try {
                    buildDependencyMap(ctx);
                } catch (MavenDownloadingException e) {
                    return e.warn(document);
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag resultTag = super.visitTag(tag, ctx);
                if (isManagedDependencyTag()) {
                    ResolvedManagedDependency managedDependency = findManagedDependency(resultTag);
                    if (managedDependency != null) {
                        mayBeUpdateVersion(managedDependency.getGroupId(), managedDependency.getArtifactId(), resultTag);
                    }
                }
                if (isDependencyTag()) {
                    ResolvedDependency dependency = findDependency(resultTag);
                    if (dependency != null) {
                        mayBeUpdateVersion(dependency.getGroupId(), dependency.getArtifactId(), resultTag);
                    }
                }
                return resultTag;
            }

            private void mayBeUpdateVersion(String groupId, String artifactId, Xml.Tag tag) {
                String dependencyVersion = springBootDependenciesMap.get(groupId + ":" + artifactId);
                if (dependencyVersion != null) {
                    Optional<Xml.Tag> version = tag.getChild("version");
                    if (!version.isPresent() || !version.get().getValue().isPresent()) {
                        return;
                    }
                    // TODO: we could use the org.openrewrite.java.dependencies.UpgradeDependencyVersion if we implement there a getVisitor with a similar logic than here,
                    //  but right now it's just a list of recipes, and the getVisitor is the default from Recipe and does nothing
                    SourceFile sourceFile = getCursor().firstEnclosing(SourceFile.class);
                    if (sourceFile instanceof Xml.Document) {
                        doAfterVisit(new org.openrewrite.maven.UpgradeDependencyVersion(groupId, artifactId, dependencyVersion, null, null, null).getVisitor());
                    } else if (sourceFile instanceof G.CompilationUnit) {
                        doAfterVisit(new org.openrewrite.gradle.UpgradeDependencyVersion(groupId, artifactId, dependencyVersion, null).getVisitor());
                    }
                }
            }

            private void buildDependencyMap(ExecutionContext ctx) throws MavenDownloadingException {
                if (springBootDependenciesMap.isEmpty()) {
                    MavenPomDownloader downloader = new MavenPomDownloader(emptyMap(), ctx,
                            getResolutionResult().getMavenSettings(), getResolutionResult().getActiveProfiles());
                    GroupArtifactVersion gav = new GroupArtifactVersion(SPRINGBOOT_GROUP, SPRING_BOOT_DEPENDENCIES, toVersion);
                    String relativePath = "";
                    List<MavenRepository> repositories = new ArrayList<>();
                    repositories.add(MavenRepository.builder()
                            .id("repository.spring.milestone")
                            .uri("https://repo.spring.io/milestone")
                            .releases(true)
                            .snapshots(true)
                            .build());
                    repositories.add(MavenRepository.builder()
                            .id("spring-snapshot")
                            .uri("https://repo.spring.io/snapshot")
                            .releases(false)
                            .snapshots(true)
                            .build());
                    repositories.add(MavenRepository.builder()
                            .id("spring-release")
                            .uri("https://repo.spring.io/release")
                            .releases(true)
                            .snapshots(false)
                            .build());
                    Pom pom = downloader.download(gav, relativePath, null, repositories);
                    ResolvedPom resolvedPom = pom.resolve(Collections.emptyList(), downloader, repositories, ctx);
                    List<ResolvedManagedDependency> dependencyManagement = resolvedPom.getDependencyManagement();
                    dependencyManagement
                            .stream()
                            .filter(d -> d.getVersion() != null)
                            .forEach(d -> springBootDependenciesMap.put(d.getGroupId() + ":" + d.getArtifactId().toLowerCase(), d.getVersion()));
                }
            }

        });
    }
}
