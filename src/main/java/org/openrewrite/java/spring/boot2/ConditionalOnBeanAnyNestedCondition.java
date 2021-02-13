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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#conditionalonbean-semantic-change"><b>ConditionalOnBean semantic change</b></a>
 * <p>
 * <b>As of SpringBoot 2.0 ConditionalOnBean uses a logical AND rather than an OR for candidate beans.</b>
 * <p>
 * {@code @ConditionalOnBean({Aa.class, Bb.class})}
 * SomeThing someThingBean(){...}
 * <p>
 * is converted to:
 * {@code @ConditionalOnBean(ConditionAaOrBb.class)}
 * SomeThing someThingBean(){...}
 * <p>
 * class ConditionAaOrBb extends AnyNestedCondition {...}
 */
public class ConditionalOnBeanAnyNestedCondition extends Recipe {
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindMultiConditionalOnBeanAnnotation();
    }

    private static class FindMultiConditionalOnBeanAnnotation extends JavaIsoVisitor<ExecutionContext> {
        private static final String CONDITIONAL_CLASS = "org.springframework.context.annotation.Conditional";
        private static final String ANY_NESTED_CONDITION_CLASS = "org.springframework.boot.autoconfigure.condition.AnyNestedCondition";
        private static final String ANY_CONDITION_TEMPLATES = "any_condition_templates";
        private static final AnnotationMatcher CONDITIONAL_BEAN_MATCHER = new AnnotationMatcher("@org.springframework.boot.autoconfigure.condition.ConditionalOnBean");

        {
            setCursoringOn();
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
            J.Annotation a = super.visitAnnotation(annotation, executionContext);

            if (CONDITIONAL_BEAN_MATCHER.matches(a) && a.getArguments() != null) {
                // First check for an array of Class arguments
                List<String> conditionalOnBeanCandidates = a.getArguments().stream()
                        .filter(p -> p instanceof J.NewArray && ((J.NewArray) p).getInitializer().size() > 1)
                        .flatMap(p -> ((J.NewArray) p).getInitializer().stream().map(J.FieldAccess.class::cast)
                                .map(J.FieldAccess::getTarget).map(J.Identifier.class::cast))
                        .map(J.Identifier::getSimpleName).collect(Collectors.toList());
                String nestedConditionParameterFormat = "%s.class";

                // If class arguments are not found then search for an array of type arguments
                if (conditionalOnBeanCandidates.isEmpty()) {
                    conditionalOnBeanCandidates = a.getArguments().stream()
                            .filter(arg -> arg instanceof J.Assignment
                                    && ((J.Assignment) arg).getAssignment() instanceof J.NewArray
                                    && ((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName().equals("type"))
                            .flatMap(assign -> ((J.NewArray) ((J.Assignment) assign).getAssignment()).getInitializer().stream()
                                    .map(l -> ((J.Literal) l).getValue().toString()))
                            .collect(Collectors.toList());
                    nestedConditionParameterFormat = "type = \"%s\"";
                }

                if (!conditionalOnBeanCandidates.isEmpty()) {
                    String conditionalClassName = conditionalClassNameFromCandidates(conditionalOnBeanCandidates);

                    // Replacing the annotation will be performed by the JavaTemplate.
                    // The associated conditional class must exist for the JavaTemplate to generate a type attributed AST
                    boolean anyConditionClassExists = getCursor().firstEnclosing(J.ClassDeclaration.class).getBody().getStatements().stream()
                            .filter(J.ClassDeclaration.class::isInstance).map(J.ClassDeclaration.class::cast)
                            .anyMatch(c -> c.getSimpleName().equals(conditionalClassName));

                    if (anyConditionClassExists) {
                        JavaTemplate t = template("@Conditional(" + conditionalClassName + ".class)")
                                .imports(CONDITIONAL_CLASS).build();
                        a = maybeAutoFormat(a, a.withTemplate(t, a.getCoordinates().replace()), executionContext, getCursor().getParentOrThrow());
                        maybeAddImport(CONDITIONAL_CLASS);
                    } else {
                        // add the new conditional class template string to the parent ClassDeclaration Cursor
                        Cursor classDeclarationCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                        Set<String> anyConditionClasses = classDeclarationCursor.getMessage(ANY_CONDITION_TEMPLATES);
                        if (anyConditionClasses == null) {
                            anyConditionClasses = new HashSet<>();
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
                    JavaTemplate t = template(s).imports(ANY_NESTED_CONDITION_CLASS).build();
                    c = maybeAutoFormat(c, c.withBody(c.getBody().withTemplate(t, c.getBody().getCoordinates().lastStatement())), executionContext);
                }
                maybeAddImport(ANY_NESTED_CONDITION_CLASS);
                // Schedule another visit to modify the associated annotations now that the new conditional classes have been added to the AST
                doAfterVisit(new ConditionalOnBeanAnyNestedCondition());
            }
            return c;
        }

        private String conditionalClassNameFromCandidates(List<String> conditionalCandidates) {
            return "Condition" + conditionalCandidates.stream().sorted().map(this::getSimpleName).collect(Collectors.joining("Or"));
        }

        private String anyConditionClassTemplate(List<String> conditionalIdentifiers, String parameterFormatString) {
            String conditionalClassFormat = "@ConditionalOnBean(" + parameterFormatString + ")class %sCondition {}";
            String conditionClassName = conditionalClassNameFromCandidates(conditionalIdentifiers);
            StringBuilder s = new StringBuilder("private class ").append(conditionClassName)
                    .append(" extends AnyNestedCondition {")
                    .append(conditionClassName).append("(){super(ConfigurationPhase.REGISTER_BEAN)}");
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
