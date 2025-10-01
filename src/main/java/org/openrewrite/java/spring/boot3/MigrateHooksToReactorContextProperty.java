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

import lombok.Data;
import lombok.Value;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.Marker;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.openrewrite.Preconditions.and;

public class MigrateHooksToReactorContextProperty extends ScanningRecipe<MigrateHooksToReactorContextProperty.ProjectsWithHooks> {
    @Override
    public String getDisplayName() {
        return "Use `spring.reactor.context-propagation` property";
    }

    @Override
    public String getDescription() {
        return "Replace `Hooks.enableAutomaticContextPropagation()` with `spring.reactor.context-propagation=true`.";
    }

    @Override
    public ProjectsWithHooks getInitialValue(ExecutionContext ctx) {
        return new ProjectsWithHooks();
    }

    private static final String SPRING_BOOT_APPLICATION_FQN = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String HOOKS_TYPE = "reactor.core.publisher.Hooks";
    private static final MethodMatcher HOOKS_MATCHER = new MethodMatcher("reactor.core.publisher.Hooks enableAutomaticContextPropagation()");

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ProjectsWithHooks acc) {
        return Preconditions.check(
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
                                Optional<JavaProject> javaProject = sourceFile.getMarkers().findFirst(JavaProject.class);
                                if (javaProject.isPresent()) {
                                    acc.projectsWithHooks.add(javaProject.get());
                                } else {
                                    acc.hasHooksInSingleProject = true;
                                }

                                getCursor().putMessageOnFirstEnclosing(JavaSourceFile.class, "has-hooks", true);
                            }
                        }
                        return mi;
                    }
                }
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ProjectsWithHooks acc) {
        if (acc.projectsWithHooks.isEmpty() && !acc.hasHooksInSingleProject) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {

                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile sourceFile = (SourceFile) tree;

                // check already processed.
                if (sourceFile.getMarkers().findFirst(PropertyAdded.class).isPresent()) {
                    return tree;
                }

                Optional<JavaProject> currentProject = sourceFile.getMarkers().findFirst(JavaProject.class);

                if (tree instanceof J.CompilationUnit) {

                    boolean shouldProcess = false;

                    if (currentProject.isPresent()) {
                        shouldProcess = acc.processedProjects.contains(currentProject.get());
                    } else if (acc.hasHooksInSingleProject) {
                        shouldProcess = true;
                    }

                    if (shouldProcess) {
                        return new HooksRemovalVisitor().visitNonNull(tree, ctx);
                    }
                }

                if (isApplicationProperties(tree)) {
                    boolean shouldAddProperty = false;

                    if (currentProject.isPresent()) {
                        JavaProject project = currentProject.get();
                        if (acc.projectsWithHooks.contains(project) &&
                                !acc.processedProjects.contains(project)) {
                            acc.processedProjects.add(project);
                            shouldAddProperty = true;
                        }
                    } else if (acc.hasHooksInSingleProject && !acc.propertiesProcessedForSingleProject) {
                        // single project or testing environment
                        acc.propertiesProcessedForSingleProject = true;
                        shouldAddProperty = true;
                    }

                    if (shouldAddProperty) {
                        Tree result = addSpringProperty(ctx, tree);

                        if (result instanceof SourceFile) {
                            result = ((SourceFile) result).withMarkers(
                                    ((SourceFile) result).getMarkers().addIfAbsent(new PropertyAdded(Tree.randomId()))
                            );
                        }
                        return result;
                    }
                }

                return tree;
            }
        };
    }

    private static boolean isApplicationProperties(@Nullable Tree tree) {
        if (tree instanceof Properties.File) {
            String fileName = ((Properties.File) tree).getSourcePath().getFileName().toString();
            return "application.properties".equals(fileName) || fileName.matches("application-.*\\.properties");
        }

        if (tree instanceof Yaml.Documents) {
            String fileName = ((Yaml.Documents) tree).getSourcePath().getFileName().toString();
            return fileName.matches("application(-.*)?\\.(yml|yaml)");
        }

        return false;
    }

    private static Tree addSpringProperty(ExecutionContext ctx, Tree properties) {
        return new AddSpringProperty("spring.reactor.context-propagation", "true", null, null)
                .getVisitor().visitNonNull(properties, ctx);
    }

    private static class HooksRemovalVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (HOOKS_MATCHER.matches(mi)) {
                maybeRemoveImport(HOOKS_TYPE);
                return null;
            }

            return mi;
        }
    }

    public static class ProjectsWithHooks {
        Set<JavaProject> projectsWithHooks = new HashSet<>();
        Set<JavaProject> processedProjects = new HashSet<>();

        boolean hasHooksInSingleProject = false; // single project or has no markers
        boolean propertiesProcessedForSingleProject = false;
    }

    @Value
    @With
    static class PropertyAdded implements Marker {
        UUID id;
    }

}
