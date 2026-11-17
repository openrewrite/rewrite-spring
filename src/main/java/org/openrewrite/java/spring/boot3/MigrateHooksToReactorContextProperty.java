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
package org.openrewrite.java.spring.boot3;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.SpringExecutionContextView;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.openrewrite.Preconditions.and;

public class MigrateHooksToReactorContextProperty extends ScanningRecipe<MigrateHooksToReactorContextProperty.ProjectsWithHooks> {
    @Getter
    final String displayName = "Use `spring.reactor.context-propagation` property";

    @Getter
    final String description = "Replace `Hooks.enableAutomaticContextPropagation()` with `spring.reactor.context-propagation=auto`.";

    @Override
    public ProjectsWithHooks getInitialValue(ExecutionContext ctx) {
        return new ProjectsWithHooks();
    }

    private static final String SPRING_BOOT_APPLICATION_FQN = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String HOOKS_PATTERN = "reactor.core.publisher.Hooks enableAutomaticContextPropagation()";
    private static final MethodMatcher HOOKS_MATCHER = new MethodMatcher(HOOKS_PATTERN);

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ProjectsWithHooks acc) {
        TreeVisitor<?, ExecutionContext> recordHooks = Preconditions.check(
                and(
                        new UsesType<>(SPRING_BOOT_APPLICATION_FQN, true),
                        new UsesMethod<>(HOOKS_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (HOOKS_MATCHER.matches(mi)) {
                            JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                            if (sourceFile != null) {
                                sourceFile.getMarkers().findFirst(JavaProject.class)
                                        .ifPresent(project -> acc.projectsWithHooks.add(project));
                            }
                        }
                        return mi;
                    }
                }
        );
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    Optional<JavaProject> project = sourceFile.getMarkers().findFirst(JavaProject.class);
                    if (project.isPresent() && isMainApplicationConfiguration(sourceFile, ctx)) {
                        acc.projectsWithConfiguration.add(project.get());
                    }
                }
                return recordHooks.visit(tree, ctx);
            }
        };
    }

    /**
     * Whether the property can be added to the configuration the application runs with. A file in a test
     * source set would leave the application itself without the property, so there the `Hooks` call is kept.
     */
    private static boolean isMainApplicationConfiguration(SourceFile sourceFile, ExecutionContext ctx) {
        if (!(sourceFile instanceof Yaml.Documents || sourceFile instanceof Properties.File)) {
            return false;
        }
        if (sourceFile.getMarkers().findFirst(SourceSet.class)
                .map(SourceSet::getName).filter("test"::equals).isPresent()) {
            return false;
        }
        for (String pathExpression : SpringExecutionContextView.view(ctx).getDefaultApplicationConfigurationPaths()) {
            if (PathUtils.matchesGlob(sourceFile.getSourcePath(), pathExpression)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ProjectsWithHooks acc) {
        if (acc.projectsWithHooks.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                // Only process files in projects where Hooks were found, and only if the property has
                // somewhere to go; otherwise removing the call would silently change the behaviour.
                Optional<JavaProject> currentProject = tree.getMarkers().findFirst(JavaProject.class);
                if (!acc.projectsWithHooks.contains(currentProject.orElse(null)) ||
                    !acc.projectsWithConfiguration.contains(currentProject.orElse(null))) {
                    return tree;
                }

                // Remove Hooks.enableAutomaticContextPropagation() calls from Java source files
                if (tree instanceof JavaSourceFile) {
                    return new RemoveMethodInvocationsVisitor(singletonList(HOOKS_PATTERN)).visitNonNull(tree, ctx);
                }

                return new AddSpringProperty("spring.reactor.context-propagation", "auto", null, null)
                        .getVisitor()
                        .visitNonNull(tree, ctx);
            }
        };
    }

    @EqualsAndHashCode
    public static class ProjectsWithHooks {
        Set<JavaProject> projectsWithHooks = new HashSet<>();
        Set<JavaProject> projectsWithConfiguration = new HashSet<>();
    }
}
