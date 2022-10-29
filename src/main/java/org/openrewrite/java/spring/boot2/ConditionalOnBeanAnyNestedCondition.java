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

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConditionalOnBeanAnyNestedCondition extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate multi-condition `@ConditionalOnBean` annotations";
    }

    @Override
    public String getDescription() {
        return "Migrate multi-condition `@ConditionalOnBean` annotations to `AnyNestedCondition`.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.autoconfigure.condition.ConditionalOnBean");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ConditionalOnBeanAnyNestedConditionVisitor();
    }

    private static class ConditionalOnBeanAnyNestedConditionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String ANY_CONDITION_TEMPLATES = "any_condition_templates";
        private static final AnnotationMatcher CONDITIONAL_BEAN = new AnnotationMatcher("@org.springframework.boot.autoconfigure.condition.ConditionalOnBean");


        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            if (CONDITIONAL_BEAN.matches(a) && a.getArguments() != null) {
                // First check for an array of Class arguments
                List<String> conditionalOnBeanCandidates = new ArrayList<>();
                for (Expression p : a.getArguments()) {
                    if (p instanceof J.NewArray) {
                        J.NewArray na = (J.NewArray) p;
                        if (na.getInitializer() != null && na.getInitializer().size() > 1) {
                            for (Expression expression : na.getInitializer()) {
                                J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                                Expression target = fieldAccess.getTarget();
                                if (target instanceof J.Identifier) {
                                    J.Identifier identifier = (J.Identifier) target;
                                    String simpleName = identifier.getSimpleName();
                                    conditionalOnBeanCandidates.add(simpleName);
                                }
                            }
                        }
                    }
                }

                String nestedConditionParameterFormat = "%s.class";

                // If class arguments are not found then search for an array of type arguments
                if (conditionalOnBeanCandidates.isEmpty()) {
                    for (Expression arg : a.getArguments()) {
                        if (arg instanceof J.Assignment
                                && ((J.Assignment) arg).getAssignment() instanceof J.NewArray
                                && "type".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName())) {
                            J.NewArray na = (J.NewArray) ((J.Assignment) arg).getAssignment();
                            if (na.getInitializer() != null) {
                                for (Expression l : na.getInitializer()) {
                                    J.Literal lit = (J.Literal) l;
                                    if (lit.getValue() != null) {
                                        conditionalOnBeanCandidates.add(lit.getValue().toString());
                                    }
                                }
                            }
                        }
                    }

                    nestedConditionParameterFormat = "type = \"%s\"";
                }

                if (!conditionalOnBeanCandidates.isEmpty()) {
                    String conditionalClassName = conditionalClassNameFromCandidates(conditionalOnBeanCandidates);

                    // Replacing the annotation will be performed by the JavaTemplate.
                    // The associated conditional class must exist for the JavaTemplate to generate a type attributed AST
                    boolean anyConditionClassExists = false;
                    for (Statement statement : getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class).getBody().getStatements()) {
                        if (statement instanceof J.ClassDeclaration) {
                            J.ClassDeclaration c = (J.ClassDeclaration) statement;
                            if (c.getSimpleName().equals(conditionalClassName)) {
                                anyConditionClassExists = true;
                                break;
                            }
                        }
                    }

                    if (anyConditionClassExists) {
                        a = a.withTemplate(JavaTemplate.builder(this::getCursor, "@Conditional(#{}.class)")
                                .imports("org.springframework.context.annotation.Conditional")
                                .javaParser(() ->
                                        JavaParser.fromJavaVersion()
                                                .dependsOn(
                                                        Stream.concat(
                                                                Stream.of(Parser.Input.fromResource("/Conditional.java")),
                                                                Parser.Input.fromResource("/AnyNestedCondition.java", "---").stream()
                                                        ).collect(Collectors.toList())
                                                )
                                                .build())
                                .build(), a.getCoordinates().replace(), conditionalClassName);
                        maybeAddImport("org.springframework.context.annotation.Conditional");
                    } else {
                        // add the new conditional class template string to the parent ClassDeclaration Cursor
                        Cursor classDeclarationCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                        Set<String> anyConditionClasses = classDeclarationCursor.getMessage(ANY_CONDITION_TEMPLATES);
                        if (anyConditionClasses == null) {
                            anyConditionClasses = new TreeSet<>();
                            classDeclarationCursor.putMessage(ANY_CONDITION_TEMPLATES, anyConditionClasses);
                        }
                        anyConditionClasses.add(anyConditionClassTemplate(conditionalOnBeanCandidates, nestedConditionParameterFormat));
                    }
                }
            }
            return a;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);
            Set<String> conditionalTemplates = getCursor().pollMessage(ANY_CONDITION_TEMPLATES);
            if (conditionalTemplates != null && !conditionalTemplates.isEmpty()) {
                for (String s : conditionalTemplates) {
                    JavaTemplate t = JavaTemplate.builder(this::getCursor, s)
                            .imports("org.springframework.boot.autoconfigure.condition.AnyNestedCondition")
                            .javaParser(() -> JavaParser.fromJavaVersion()
                                    .dependsOn(Parser.Input.fromResource("/AnyNestedCondition.java", "---"))
                                    .build())
                            .build();
                    c = maybeAutoFormat(c, c.withBody(c.getBody().withTemplate(t, c.getBody().getCoordinates().lastStatement())), executionContext);
                }

                // Schedule another visit to modify the associated annotations now that the new conditional classes have been added to the AST
                doAfterVisit(new ConditionalOnBeanAnyNestedConditionVisitor());
                maybeAddImport("org.springframework.boot.autoconfigure.condition.AnyNestedCondition");
            }
            return c;
        }

        private String conditionalClassNameFromCandidates(List<String> conditionalCandidates) {
            return "Condition" + conditionalCandidates.stream().sorted().map(this::getSimpleName).collect(Collectors.joining("Or"));
        }

        private String anyConditionClassTemplate(List<String> conditionalIdentifiers, String parameterFormatString) {
            String conditionalClassFormat = "@ConditionalOnBean(" + parameterFormatString + ")class %sCondition {}";
            String conditionClassName = conditionalClassNameFromCandidates(conditionalIdentifiers);
            StringBuilder s = new StringBuilder("private static class ").append(conditionClassName)
                    .append(" extends AnyNestedCondition {")
                    .append(conditionClassName).append("(){super(ConfigurationPhase.REGISTER_BEAN);}");
            conditionalIdentifiers.stream().sorted().forEach(ci -> s.append(String.format(conditionalClassFormat, ci, getSimpleName(ci))));
            s.append("}");
            return s.toString();
        }

        private String getSimpleName(String type) {
            String t = type;
            if (t.endsWith(".class")) {
                t = t.substring(0, t.lastIndexOf("."));
            }
            if (t.contains(".")) {
                t = t.substring(t.lastIndexOf(".") + 1);
            }
            return t;
        }
    }
}
