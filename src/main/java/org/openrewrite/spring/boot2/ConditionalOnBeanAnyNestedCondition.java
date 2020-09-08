/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring.boot2;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.java.GenerateConstructorUsingFields;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#conditionalonbean-semantic-change
 */
@AutoConfigure
public class ConditionalOnBeanAnyNestedCondition extends JavaRefactorVisitor {
    private static final String ANY_NESTED_CONDITION = "org.springframework.boot.autoconfigure.condition.AnyNestedCondition";
    private static final FindAnnotations findNestedConditions = new FindAnnotations("@org.springframework.boot.autoconfigure.condition.ConditionalOnBean");

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if (findNestedConditions.visit(classDecl).size() > 1) {
            andThen(new ExtendAnyNestedCondition(classDecl));
        }
        return super.visitClassDecl(classDecl);
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    private static class ExtendAnyNestedCondition extends JavaRefactorVisitor {
        private final J.ClassDecl scope;

        private ExtendAnyNestedCondition(J.ClassDecl scope) {
            this.scope = scope;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

            // because of Java's single inheritance, even if it extends from a
            // class other than AnyNestedCondition, there's nothing we can do.
            if (scope.isScope(c) && classDecl.getExtends() == null) {
                c = c.withExtends(new J.ClassDecl.Extends(
                        randomId(),
                        J.Ident.build(randomId(), "AnyNestedCondition",
                                JavaType.Class.build(ANY_NESTED_CONDITION),
                                format(" ")),
                        format(" "))
                );

                maybeAddImport(ANY_NESTED_CONDITION);

                if (classDecl.getMethods().stream().noneMatch(J.MethodDecl::isConstructor)) {
                    andThen(new GenerateConstructorUsingFields.Scoped(classDecl, emptyList()));
                }

                andThen(new CallSuperWithConfigurationPhase(classDecl));
            }

            return c;
        }
    }

    private static class CallSuperWithConfigurationPhase extends JavaRefactorVisitor {
        private static final String CONFIGURATION_PHASE = "org.springframework.context.annotation.ConfigurationPhase";
        private final J.ClassDecl scope;

        private CallSuperWithConfigurationPhase(J.ClassDecl scope) {
            this.scope = scope;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

            if (!scope.isScope(classDecl)) {
                return c;
            }

            // super(ConfigurationPhase.REGISTER_BEAN)
            JavaType.Class configurationPhaseType = JavaType.Class.build(CONFIGURATION_PHASE);
            J.MethodInvocation callSuper = new J.MethodInvocation(randomId(),
                    null,
                    null,
                    J.Ident.build(randomId(), "super", JavaType.Class.build(ANY_NESTED_CONDITION), Formatting.EMPTY),
                    new J.MethodInvocation.Arguments(
                            randomId(),
                            singletonList(
                                    new J.FieldAccess(
                                            randomId(),
                                            J.Ident.build(randomId(), "ConfigurationPhase", configurationPhaseType, Formatting.EMPTY),
                                            J.Ident.build(randomId(), "REGISTER_BEAN", configurationPhaseType, Formatting.EMPTY),
                                            configurationPhaseType,
                                            Formatting.EMPTY
                                    )
                            ),
                            Formatting.EMPTY),
                    null,
                    Formatting.EMPTY
            );

            Optional<J.MethodDecl> ctor = classDecl.getMethods().stream()
                    .filter(J.MethodDecl::isConstructor)
                    .max(comparing(m -> m.getParams().getParams().size()));

            if (ctor.isPresent()) {
                List<Statement> statements = new ArrayList<>(ctor.get().getBody().getStatements());
                statements.add(0, callSuper.withFormatting(formatter.format(ctor.get().getBody())));
                c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                    .map(s -> s == ctor.get() ? ctor.get().withBody(ctor.get().getBody().withStatements(statements)) : s)
                    .collect(toList())));
            }

            maybeAddImport(CONFIGURATION_PHASE);

            return c;
        }
    }
}
