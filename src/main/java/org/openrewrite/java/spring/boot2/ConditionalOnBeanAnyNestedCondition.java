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
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.HashSet;
import java.util.Set;

/**
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#conditionalonbean-semantic-change"><b>ConditionalOnBean semantic change</b></a>
 * <p>
 * Any class which does not extend any other class and has multiple inner-classes annotated with <a href="https://docs.spring.io/spring-boot/docs/1.5.x/api/org/springframework/boot/autoconfigure/condition/ConditionalOnBean.html">ConditionalOnBean</a>
 * will update to extend <a href="https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/condition/AnyNestedCondition.html">AnyNestedCondition</a>
 * <p>
 * The constructor of the modified class will be updated with a statement invoking the AnyNestedCondition constructor where the ConfigurationPhase argument is
 * <a href="https://docs.spring.io/spring-framework/docs/5.3.3/javadoc-api/org/springframework/context/annotation/ConfigurationCondition.ConfigurationPhase.html#REGISTER_BEAN">REGISTER_BEAN</a>
 *
 *
 */
//TODO:  Revisit this recipe so that it handles multiple conditional annotations and double check the
public class ConditionalOnBeanAnyNestedCondition extends Recipe {
    private static final String CONDITIONAL_BEAN_CLASS = "@org.springframework.boot.autoconfigure.condition.ConditionalOnBean";

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ConditionalOnBeanAnyNestedConditionVisitor();
    }

    /**
     * ClassDeclaration Visitor which will add {@link ExtendAnyNestedConditionVisitor} to the afterVisit list
     * for any class having multiple nested classes annotated with @ConditionalOnBean
     */
    private static class ConditionalOnBeanAnyNestedConditionVisitor extends JavaIsoVisitor<ExecutionContext> {

        {
            setCursoringOn();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cdl = super.visitClassDeclaration(classDecl, executionContext);
            Set<J.Annotation> annotationReferences = new HashSet<>();
            new CollectConditionalOnBeanAnnotationsVisitor().visit(cdl, annotationReferences);
            if (annotationReferences.size() > 1) {
                doAfterVisit(new ExtendAnyNestedConditionVisitor(cdl));
            }
            return cdl;
        }
    }

    private static class CollectConditionalOnBeanAnnotationsVisitor extends JavaIsoVisitor<Set<J.Annotation>> {
        private static final AnnotationMatcher matcher = new AnnotationMatcher(CONDITIONAL_BEAN_CLASS);
        {
            setCursoringOn();
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, Set<J.Annotation> conditionalOnBeanAnnotations) {
            J.Annotation a = super.visitAnnotation(annotation, conditionalOnBeanAnnotations);
            if (matcher.matches(a) && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                conditionalOnBeanAnnotations.add(a);
            }
            return a;
        }
    }

    /**
     * If the configuration class does not already extend another class
     * then extend it with AnyNestedCondition then invoke the {@link CallSuperWithConfigurationPhaseVisitor} visitor for
     * adding a call to super(CofigurationPhase.REGISTER_BEAN)
     */
    private static class ExtendAnyNestedConditionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String ANY_NESTED_CONDITION = "org.springframework.boot.autoconfigure.condition.AnyNestedCondition";

        private final J.ClassDeclaration scope;

        private ExtendAnyNestedConditionVisitor(J.ClassDeclaration scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);

            // because of Java's single inheritance, even if it extends from a
            // class other than AnyNestedCondition, there's nothing we can do.
            if (scope.isScope(c) && classDecl.getExtends() == null) {
                // Add the extends once JavaTemplate is fixed
                c = c.withTemplate(template("extends AnyNestedCondition").imports(ANY_NESTED_CONDITION).build(), c.getCoordinates().replaceExtendsClause());
                maybeAddImport(ANY_NESTED_CONDITION);
                if (classDecl.getBody().getStatements().stream().noneMatch(statement -> statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).isConstructor())) {
                    String constructorStatement = "public #{}() {super(ConfigurationPhase.REGISTER_BEAN);}";
                    c = maybeAutoFormat(c, c.withTemplate(template(constructorStatement).build(),
                            c.getBody().getStatements().get(0).getCoordinates().before(), classDecl.getName().getSimpleName()), executionContext);
                } else {
                    for (Statement statement : c.getBody().getStatements()) {
                        if (statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).isConstructor()) {
                            doAfterVisit(new CallSuperWithConfigurationPhaseVisitor(((J.MethodDeclaration) statement).getBody()));
                        }
                    }
                }
            }

            return c;
        }
    }

    /**
     * Add or modify an existing constructor with a new statement
     * for calling -> super(configurationPhase.REGISTER_BEAN);
     *
     * if an empty constructor is encountered then auto-format the MethodDeclaration constructor
     * with the {@link AutoFormatModifiedConstructorVisitor}
     */
    private static class CallSuperWithConfigurationPhaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.Block scope;
        public CallSuperWithConfigurationPhaseVisitor(J.Block scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext executionContext) {
            J.Block b = super.visitBlock(block, executionContext);
            if (scope.isScope(b)) {
                JavaCoordinates statementCoordinates;
                if (b.getStatements().isEmpty()) {
                    statementCoordinates = b.getCoordinates().lastStatement();
                    doAfterVisit(new AutoFormatModifiedConstructorVisitor(getCursor().firstEnclosingOrThrow(J.MethodDeclaration.class)));
                } else {
                    statementCoordinates = b.getStatements().get(0).getCoordinates().before();
                }
                b = b.withTemplate(template("super(ConfigurationPhase.REGISTER_BEAN);").build(), statementCoordinates);
            }
            return b;
        }
    }

    /**
     * AutoFormat should be performed on the MethodDeclaration when adding a new statement to an empty Block
     */
    private static class AutoFormatModifiedConstructorVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodDeclaration scope;

        public AutoFormatModifiedConstructorVisitor(J.MethodDeclaration scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
            if (scope.isScope(m)) {
                m = (J.MethodDeclaration)new AutoFormatVisitor<>().visit(m, executionContext, getCursor().getParentOrThrow());
            }
            return m;
        }
    }
}
