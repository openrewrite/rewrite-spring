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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.*;
import org.openrewrite.semver.XRange;
import org.openrewrite.xml.tree.Xml;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpgradeExplicitSpringBootDependencies extends ScanningRecipe<UpgradeExplicitSpringBootDependencies.Accumulator> {

    private static final String SPRINGBOOT_GROUP = "org.springframework.boot";
    private static final String SPRING_BOOT_DEPENDENCIES = "spring-boot-dependencies";

    @Option(displayName = "From Spring version",
            description = "XRage pattern for spring version used to limit which projects should be updated",
            example = " 2.7.+")
    String fromVersion;

    @Option(displayName = "To Spring version",
            description = "Upgrade version of `org.springframework.boot`",
            example = "3.0.0-M3")
    String toVersion;

    @Override
    public String getDisplayName() {
        return "Upgrade Spring dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrades dependencies according to the specified version of spring boot. " +
               "Spring boot has many direct and transitive dependencies. When a module has an explicit dependency on " +
               "one of these it may also need to be upgraded to match the version used by spring boot.";
    }

    @Data
    public static class Accumulator {
        UpgradeDependencyVersion.Accumulator udvAcc = new UpgradeDependencyVersion.Accumulator(
                new org.openrewrite.maven.UpgradeDependencyVersion.Accumulator(),
                new org.openrewrite.gradle.UpgradeDependencyVersion.DependencyVersionState()
        );
        List<MavenRepository> repositories = new ArrayList<>();
        Map<String, String> springBootDependenciesMap = new HashMap<>();
        @Nullable
        MavenDownloadingException mavenDownloadingException = null;
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
                        return SearchResult.found(resultTag);
                    }
                }

                if (isDependencyTag()) {
                    ResolvedDependency dependency = findDependency(resultTag);
                    if ((dependency != null) && dependency.getGroupId().equals(SPRINGBOOT_GROUP)
                        && satisfiesOldVersionPattern(dependency.getVersion())) {
                        return SearchResult.found(resultTag);
                    }
                }
                return resultTag;
            }

            private boolean satisfiesOldVersionPattern(@Nullable String version) {
                return version != null && XRange.build(fromVersion, version).isValid();
            }
        };
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        //noinspection NullableProblems
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                TreeVisitor<?, ExecutionContext> udvScanner = new UpgradeDependencyVersion("", "", "", null, null, null)
                        .getScanner(acc.getUdvAcc());
                if (udvScanner.isAcceptable((SourceFile) tree, ctx)) {
                    udvScanner.visit(tree, ctx);
                }

                Optional<GradleProject> maybeGp = tree.getMarkers()
                        .findFirst(GradleProject.class);
                if (maybeGp.isPresent()) {
                    GradleProject gp = maybeGp.get();
                    acc.repositories.addAll(gp.getMavenRepositories());
                }
                Optional<MavenResolutionResult> maybeMrr = tree.getMarkers()
                        .findFirst(MavenResolutionResult.class);
                if (maybeMrr.isPresent()) {
                    MavenResolutionResult mrr = maybeMrr.get();
                    acc.repositories.addAll(mrr.getPom().getRepositories());
                }

                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, Collection<SourceFile> generatedInThisCycle, ExecutionContext ctx) {
        List<MavenRepository> repositories = acc.getRepositories();
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

        MavenPomDownloader downloader = new MavenPomDownloader(emptyMap(), ctx);
        GroupArtifactVersion gav = new GroupArtifactVersion(SPRINGBOOT_GROUP, SPRING_BOOT_DEPENDENCIES, toVersion);
        String relativePath = "";

        try {
            Pom pom = downloader.download(gav, relativePath, null, repositories);
            ResolvedPom resolvedPom = pom.resolve(emptyList(), downloader, repositories, ctx);
            List<ResolvedManagedDependency> dependencyManagement = resolvedPom.getDependencyManagement();
            dependencyManagement
                    .stream()
                    .filter(d -> d.getVersion() != null)
                    .forEach(d -> acc.getSpringBootDependenciesMap().put(d.getGroupId() + ":" + d.getArtifactId().toLowerCase(), d.getVersion()));
        } catch (MavenDownloadingException e) {
            acc.mavenDownloadingException = e;
        }
        return emptyList();
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        return super.generate(acc, ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(precondition(), new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if(acc.mavenDownloadingException != null) {
                    return acc.mavenDownloadingException.warn(document);
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag resultTag = super.visitTag(tag, ctx);
                if (isManagedDependencyTag()) {
                    ResolvedManagedDependency managedDependency = findManagedDependency(resultTag);
                    if (managedDependency != null) {
                        mayBeUpdateVersion(acc, managedDependency.getGroupId(), managedDependency.getArtifactId(), resultTag);
                    }
                }
                if (isDependencyTag()) {
                    ResolvedDependency dependency = findDependency(resultTag);
                    if (dependency != null) {
                        mayBeUpdateVersion(acc, dependency.getGroupId(), dependency.getArtifactId(), resultTag);
                    }
                }
                return resultTag;
            }

            private void mayBeUpdateVersion(Accumulator acc, String groupId, String artifactId, Xml.Tag tag) {
                String dependencyVersion = acc.springBootDependenciesMap.get(groupId + ":" + artifactId);
                if (dependencyVersion != null) {
                    Optional<Xml.Tag> version = tag.getChild("version");
                    if (!version.isPresent() || !version.get().getValue().isPresent()) {
                        return;
                    }
                    doAfterVisit(new org.openrewrite.java.dependencies.UpgradeDependencyVersion(groupId, artifactId, dependencyVersion, null, true, null)
                            .getVisitor(acc.getUdvAcc()));
                }
            }
        });
    }
}
