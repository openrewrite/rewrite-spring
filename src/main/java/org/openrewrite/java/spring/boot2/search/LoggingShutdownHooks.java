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
package org.openrewrite.java.spring.boot2.search;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.DependencyMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Mark POM's of projects where logging shutdown hook may need to be disabled
 *
 * @author Alex Boyko
 */
public class LoggingShutdownHooks extends ScanningRecipe<AtomicBoolean> {

    @Override
    public String getDisplayName() {
        return "Applications using logging shutdown hooks";
    }

    @Override
    public String getDescription() {
        return "Spring Boot registers a logging shutdown hook by default for JAR-based applications to ensure that " +
                "logging resources are released when the JVM exits. If your application is deployed as a WAR then " +
                "the shutdown hook is not registered since the servlet container usually handles logging concerns." +
                "\n\n" +
                "Most applications will want the shutdown hook. However, if your application has complex context " +
                "hierarchies, then you may need to disable it. You can use the `logging.register-shutdown-hook` " +
                "property to do that.";
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
                if (acc.get()) {
                    return document;
                }
                MavenResolutionResult model = getResolutionResult();
                //Default packaging, if not specified is "jar"
                if (!"jar".equals(model.getPom().getPackaging())) {
                    return document;
                }
                DependencyMatcher matcher = requireNonNull(DependencyMatcher.build("org.springframework.boot:spring-boot:2.4.X").getValue());
                for (ResolvedDependency d : getResolutionResult().getDependencies().getOrDefault(Scope.Compile, Collections.emptyList())) {
                    if (matcher.matches(d.getGroupId(), d.getArtifactId(), d.getVersion())) {
                        acc.set(true);
                    }
                }
                return document;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        return Preconditions.firstAcceptable(
                Preconditions.check(acc.get(),
                        new FindAnnotations("@org.springframework.boot.autoconfigure.SpringBootApplication", null).getVisitor()));
    }

}
