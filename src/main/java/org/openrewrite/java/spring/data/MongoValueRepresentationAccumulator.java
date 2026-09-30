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
package org.openrewrite.java.spring.data;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.data.search.FindMissingMongoValueRepresentation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Scan state shared between {@link FindMissingMongoValueRepresentation} and
 * {@link AddMongoValueRepresentationProperty}, populated by {@link MongoValueRepresentationScanner}.
 */
public class MongoValueRepresentationAccumulator {

    private final Map<MongoValueRepresentationKind, Set<JavaProject>> validlyConfigured = new ConcurrentHashMap<>();
    private final Map<MongoValueRepresentationKind, Set<JavaProject>> propertyAttempted = new ConcurrentHashMap<>();
    private final Map<MongoValueRepresentationKind, Set<JavaProject>> javaAttempted = new ConcurrentHashMap<>();
    private final Map<JavaProject, ConcurrentLinkedQueue<Occurrence>> occurrences = new ConcurrentHashMap<>();
    private final Map<JavaProject, ConcurrentLinkedQueue<ConfigurationIssue>> configurationIssues = new ConcurrentHashMap<>();
    private final Map<JavaProject, Path> preferredConfigurationSource = new ConcurrentHashMap<>();
    private final Set<JavaProject> rowsInserted = ConcurrentHashMap.newKeySet();

    @Value
    public static class Occurrence {
        Path sourcePath;
        UUID owningClassId;
        String owningType;
        String field;
        MongoValueRepresentationKind kind;
    }

    @Value
    public static class ConfigurationIssue {
        Path sourcePath;
        UUID treeId;
        MongoValueRepresentationKind kind;
    }

    public void markConfigured(MongoValueRepresentationKind kind, JavaProject project) {
        validlyConfigured.computeIfAbsent(kind, ignored -> ConcurrentHashMap.newKeySet()).add(project);
    }

    public boolean isUnconfigured(MongoValueRepresentationKind kind, JavaProject project) {
        Set<JavaProject> configured = validlyConfigured.get(kind);
        return configured == null || !configured.contains(project);
    }

    public void markPropertyAttempted(MongoValueRepresentationKind kind, JavaProject project) {
        propertyAttempted.computeIfAbsent(kind, ignored -> ConcurrentHashMap.newKeySet()).add(project);
    }

    public boolean isPropertyUnattempted(MongoValueRepresentationKind kind, JavaProject project) {
        Set<JavaProject> attempted = propertyAttempted.get(kind);
        return attempted == null || !attempted.contains(project);
    }

    public void markJavaAttempted(MongoValueRepresentationKind kind, JavaProject project) {
        javaAttempted.computeIfAbsent(kind, ignored -> ConcurrentHashMap.newKeySet()).add(project);
    }

    public boolean isJavaUnattempted(MongoValueRepresentationKind kind, JavaProject project) {
        Set<JavaProject> attempted = javaAttempted.get(kind);
        return attempted == null || !attempted.contains(project);
    }

    public void addOccurrence(JavaProject project, Occurrence occurrence) {
        occurrences.computeIfAbsent(project, ignored -> new ConcurrentLinkedQueue<>()).add(occurrence);
    }

    public Set<JavaProject> projectsWithOccurrences() {
        return occurrences.keySet();
    }

    public List<Occurrence> projectOccurrences(JavaProject project) {
        ConcurrentLinkedQueue<Occurrence> found = occurrences.get(project);
        return found == null ? Collections.emptyList() : new ArrayList<>(found);
    }

    public List<Occurrence> unresolvedOccurrences(JavaProject project) {
        List<Occurrence> unresolved = new ArrayList<>();
        for (Occurrence occurrence : projectOccurrences(project)) {
            if (isUnconfigured(occurrence.getKind(), project)) {
                unresolved.add(occurrence);
            }
        }
        return unresolved;
    }

    public void addConfigurationIssue(JavaProject project, ConfigurationIssue issue) {
        configurationIssues.computeIfAbsent(project, ignored -> new ConcurrentLinkedQueue<>()).add(issue);
    }

    public List<ConfigurationIssue> configurationIssuesIn(Path sourcePath, JavaProject project,
                                                            List<Occurrence> occurrences) {
        List<ConfigurationIssue> issues = new ArrayList<>();
        ConcurrentLinkedQueue<ConfigurationIssue> found = configurationIssues.get(project);
        if (found != null) {
            for (ConfigurationIssue issue : found) {
                if (issue.getSourcePath().equals(sourcePath) && hasKind(occurrences, issue.getKind())) {
                    issues.add(issue);
                }
            }
        }
        return issues;
    }

    public void mergePreferredConfigurationSource(JavaProject project, Path candidate) {
        preferredConfigurationSource.merge(project, candidate, SpringConfigFileSupport::preferredConfigurationSource);
    }

    public @Nullable Path preferredConfigurationSource(JavaProject project) {
        return preferredConfigurationSource.get(project);
    }

    public boolean markRowsInserted(JavaProject project) {
        return rowsInserted.add(project);
    }

    public static boolean hasKind(List<Occurrence> occurrences, MongoValueRepresentationKind kind) {
        return occurrences.stream().anyMatch(occurrence -> occurrence.getKind() == kind);
    }
}
