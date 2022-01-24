/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Collection;

/**
 * Mark Pom's of projects where logging shutdown hook may need to be disabled
 *
 * @author Alex Boyko
 */
public class LoggingShutdownHooksRecipe extends Recipe {

    private static final String NEEDS_SEARCH_MARKER = "NEEDS_SEARCH_MARKER";

    private static final XPathMatcher PROJECT_MATCHER = new XPathMatcher("/project");

    private static final String SEARCH_MARKER_DESCRIPTION = "LoggingShutDownHook";

    @Override
    public String getDisplayName() {
        return "Logging Shutdown Hooks";
    }

    @Override
    public String getDescription() {
        return "We now register a logging shutdown hook by default for jar based applications to ensure that " +
                "logging resources are released when the JVM exits. If your application is deployed as a war then " +
                "the shutdown hook is not registered since the servlet container usually handles logging concerns" +
                "\n\n" +
                "Most applications will want the shutdown hook. However, if your application has complex context" +
                " hierarchies, then you may need to disable it. You can use the logging.register-shutdown-hook" +
                " property to do that.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new MavenVisitor() {

            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                Collection<Pom.Dependency> deps = maven.getModel().getDependencies(Scope.Compile);
                if (deps.stream()
                        .filter(d -> d.getArtifactId().equals("spring-boot")
                                && d.getVersion().startsWith("2.5.")
                                && d.getGroupId().equals("org.springframework.boot"))
                        .findFirst()
                        .isPresent()
                ) {
                    return maven.withMarkers(maven.getMarkers().searchResult());
                }
                return maven;
            }

        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenVisitor() {
            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                Maven m = super.visitMaven(maven, ctx);
                if (getCursor().pollMessage(NEEDS_SEARCH_MARKER) != null) {
                    m = m.withMarkers(maven.getMarkers().searchResult(SEARCH_MARKER_DESCRIPTION));
                }
                return m;
            }

            @Override
            public Xml visitTag(Xml.Tag tag, ExecutionContext context) {
                if (PROJECT_MATCHER.matches(getCursor())
                        && tag.getChildValue("packaging").orElse("jar").equalsIgnoreCase("jar")) {
                    getCursor().dropParentUntil(Maven.class::isInstance).putMessage(NEEDS_SEARCH_MARKER, true);
                }
                return tag;
            }
        };
    }

}
