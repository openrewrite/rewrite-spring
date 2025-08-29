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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.Preconditions.and;

public class MigrateHooksToReactorContextProperty extends ScanningRecipe<AtomicBoolean> {
    @Override
    public String getDisplayName() {
        return "Use `spring.reactor.context-propagation` property";
    }

    @Override
    public String getDescription() {
        return "Replace `Hooks.enableAutomaticContextPropagation()` with `spring.reactor.context-propagation=true`.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    private static final String SPRING_BOOT_APPLICATION_FQN = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String HOOKS_TYPE = "reactor.core.publisher.Hooks";
    private static final MethodMatcher HOOKS_MATCHER = new MethodMatcher("reactor.core.publisher.Hooks enableAutomaticContextPropagation()");

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean foundHooksInSpringApp) {
        return Preconditions.check(
                and(
                        new UsesType<>(SPRING_BOOT_APPLICATION_FQN, true),
                        new UsesType<>(HOOKS_TYPE, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (HOOKS_MATCHER.matches(mi)) {
                            foundHooksInSpringApp.set(true);
                        }

                        return mi;
                    }
                }
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean foundHooksInSpringApp) {
        if (!foundHooksInSpringApp.get()) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return new HooksRemovalVisitor().visitNonNull(tree, ctx);
                }

                if (isApplicationProperties(tree)) {
                    return addSpringProperty(ctx, tree, "spring.reactor.context-propagation", "true");
                }

                return tree;
            }
        };
    }

    private static boolean isApplicationProperties(@Nullable Tree tree) {
        return (tree instanceof Properties.File &&
                "application.properties".equals(((Properties.File) tree).getSourcePath().getFileName().toString())) ||
                (tree instanceof Yaml.Documents &&
                        ((Yaml.Documents) tree).getSourcePath().getFileName().toString().matches("application\\.ya*ml"));
    }

    private static Tree addSpringProperty(ExecutionContext ctx, Tree properties, String property, String value) {
        return new AddSpringProperty(property, value, null, null)
                .getVisitor()
                .visitNonNull(properties, ctx);
    }

    private static class HooksRemovalVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            // Only remove if we're in a @SpringBootApplication class
            if (HOOKS_MATCHER.matches(mi)) {
                maybeRemoveImport(HOOKS_TYPE);
                return null;
            }

            return mi;
        }
    }
}
