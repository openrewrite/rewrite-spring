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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.Scope;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Migration for Spring Boot 2.4 to 2.5
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.5-Release-Notes#messages-in-the-default-error-view">Messages in the Default Error View</a>
 */
public class MessagesInTheDefaultErrorView extends Recipe {

    private static final String SEARCH_MARKER_DESCRIPTION = "MessagesInTheDefaultErrorView";
    private final Set<String> relevantDependencies  = Stream.of(
            "org.springframework:spring-webmvc",
            "org.springframework:spring-webflux"
    ).collect(Collectors.toSet());

    @Override
    public String getDisplayName() {
        return "The message attribute in the default error view is now removed in Spring Boot 2.5.";
    }

    @Override
    public String getDescription() {
        return "The recipe found a dependency on `org.springframework:spring-webmvc` or `org.springframework:spring-webflux`.\n" +
                "The `message` attribute in the default error view was removed in Spring Boot 2.5 rather than blanked when it is not shown.\n" +
                "If you parse the error response JSON, you may need to deal with the missing item.\n" +
                "You can still use the `server.error.include-message` property if you want messages to be included.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenVisitor() {


            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                Maven m = super.visitMaven(maven, ctx);

                if(isNotMarkedAsSearchResult(m) && hasDependencyWithDefaultErrorView(m)) {
                    m = markAsMatch(m);
                }

                return m;
            }

            private Maven markAsMatch(Maven maven) {
                return maven.withMarkers(maven.getMarkers().add(new SearchResult(UUID.randomUUID(), SEARCH_MARKER_DESCRIPTION)));
            }

            private boolean isNotMarkedAsSearchResult(Maven md) {
                return md.getMarkers().getMarkers().stream()
                        .filter(m -> m instanceof SearchResult)
                        .map(SearchResult.class::cast)
                        .noneMatch(sr -> SEARCH_MARKER_DESCRIPTION.equals(sr.getDescription()));
            }

            private boolean hasDependencyWithDefaultErrorView(Maven m) {
                return m.getModel().getDependencies(Scope.Compile).stream()
                        .anyMatch(this::isDependencyDefiningDEfaultErrorView);
            }

            private boolean isDependencyDefiningDEfaultErrorView(Pom.Dependency dependency) {
                return relevantDependencies.contains(dependency.getGroupId() + ":" + dependency.getArtifactId());
            }

        };
    }
}
