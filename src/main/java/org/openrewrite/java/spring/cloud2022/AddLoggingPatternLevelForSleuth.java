/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.cloud2022;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.DependencyMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

public class AddLoggingPatternLevelForSleuth extends ScanningRecipe<AtomicBoolean> {
    @Override
    public String getDisplayName() {
        return "Add logging.pattern.level for traceId and spanId";
    }

    @Override
    public String getDescription() {
        return "Add `logging.pattern.level` for traceId and spanId which was previously set by default, if not already set.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (!acc.get()) {
                    DependencyMatcher matcher = DependencyMatcher.build("org.springframework.cloud:spring-cloud-starter-sleuth:X").getValue();
                    List<ResolvedDependency> dependencies = getResolutionResult().getDependencies().getOrDefault(Scope.Compile, emptyList());
                    acc.set(dependencies.stream().anyMatch(d -> matcher.matches(d.getGroupId(), d.getArtifactId(), d.getVersion())));
                }
                return document;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        AddSpringProperty addSpringProperty = new AddSpringProperty(
                "logging.pattern.level",
                // The ${spring.application.name:} could not be escaped in yaml so far
                "\"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]\"",
                "Logging pattern containing traceId and spanId; no longer provided through Sleuth by default",
                null);
        return Preconditions.check(acc.get(), addSpringProperty.getVisitor());
    }
}
