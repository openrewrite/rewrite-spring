/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReplaceSupportClassWithItsInterface extends Recipe {

    @Override
    public String getDisplayName() {
        return "Transform classes that extend a given Class to implement the given Interface instead";
    }

    @Override
    public String getDescription() {
        return "As of Spring-Batch 5.0 Listeners has default methods (made possible by a Java 8 baseline) and can be implemented directly without the need for this adapter.";
    }

    @Option(displayName = "Fully Qualified Class Name", description = "A fully-qualified class name to be replaced.", example = "org.springframework.batch.core.listener.JobExecutionListenerSupport")
    @NonNull
    private String fullyQualifiedClassName;

    @Option(displayName = "Fully Qualified Interface Name", description = "A fully-qualified Interface name to replace by.", example = "org.springframework.batch.core.JobExecutionListener")
    @NonNull
    private String fullyQualifiedInterfaceName;

    public String getFullyQualifiedClassName() {
        return fullyQualifiedClassName;
    }

    public String getFullyQualifiedInterfaceName() {
        return fullyQualifiedInterfaceName;
    }

    private final String simpleInterfaceName;

    // Recipes must be serializable. This is verified by RecipeTest.assertChanged()
    // and RecipeTest.assertUnchanged()
    @JsonCreator
    public ReplaceSupportClassWithItsInterface(
            @NonNull @JsonProperty("fullyQualifiedClassName")String fullyQualifiedClassName,
            @NonNull @JsonProperty("fullyQualifiedInterfaceName")String fullyQualifiedInterfaceName) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.fullyQualifiedInterfaceName = fullyQualifiedInterfaceName;
        this.simpleInterfaceName = JavaType.ShallowClass.build(fullyQualifiedInterfaceName).getClassName();
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(fullyQualifiedClassName);
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                    ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                if (cd.getExtends() != null
                        && TypeUtils.isOfClassType(cd.getExtends().getType(), fullyQualifiedClassName)) {
                    cd = cd.withExtends(null);
                    // This is an interesting one... JobExecutionListenerSupport implements
                    // JobExecutionListener
                    // remove the super type from the class type to prevent a stack-overflow
                    // exception when the JavaTemplate visits class type.
                    JavaType.Class type = (JavaType.Class) cd.getType();
                    if (type != null) {
                        cd = cd.withType(type.withSupertype(null));
                    }

                    cd = cd.withTemplate(
                            JavaTemplate
                                    .builder(() -> getCursor().dropParentUntil(
                                            p -> p instanceof J.ClassDeclaration || p instanceof J.CompilationUnit),
                                            simpleInterfaceName)
                                    .imports(fullyQualifiedInterfaceName)
                                    .javaParser(() -> JavaParser.fromJavaVersion().classpath("spring-batch").build())
                                    .build(),
                            cd.getCoordinates().addImplementsClause());
                    cd = (J.ClassDeclaration) new RemoveSuperStatementVisitor().visitNonNull(cd, executionContext,
                            getCursor());
                    maybeRemoveImport(fullyQualifiedClassName);
                    maybeAddImport(fullyQualifiedInterfaceName);
                }
                return cd;
            }

            class RemoveSuperStatementVisitor extends JavaIsoVisitor<ExecutionContext> {
                final MethodMatcher wm = new MethodMatcher(fullyQualifiedClassName + " *(..)");

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                        ExecutionContext executionContext) {
                    J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                    if (wm.matches(method.getMethodType())) {
                        return null;
                    }
                    return mi;
                }
            }
        };
    }
}
