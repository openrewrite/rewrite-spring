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
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.DependencyMatcher;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Mark POM's of projects where logging shutdown hook may need to be disabled
 *
 * @author Alex Boyko
 */
public class LoggingShutdownHooks extends Recipe {
    private static final String NEEDS_SEARCH_MARKER = "NEEDS_SEARCH_MARKER";
    private static final XPathMatcher PROJECT_MATCHER = new XPathMatcher("/project");

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
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new MavenVisitor() {
            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                if(!maven.getModel().getPackaging().equals("jar")) {
                    return maven;
                }

                DependencyMatcher matcher = DependencyMatcher.build("org.springframework.boot:spring-boot:2.5.X").getValue();
                assert matcher != null;
                for (Pom.Dependency d : maven.getModel().getDependencies(Scope.Compile)) {
                    if (matcher.matches(d.getGroupId(), d.getArtifactId(), d.getVersion())) {
                        return maven.withMarkers(maven.getMarkers().searchResult());
                    }
                }
                return maven;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        FindAnnotations findAnnotations = new FindAnnotations("@org.springframework.boot.autoconfigure.SpringBootApplication");
        try {
            // FIXME make this method public on FindAnnotations

            Method getVisitor = Recipe.class.getDeclaredMethod("getVisitor");
            getVisitor.setAccessible(true);

            //noinspection unchecked
            return (TreeVisitor<?, ExecutionContext>) getVisitor.invoke(findAnnotations);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
