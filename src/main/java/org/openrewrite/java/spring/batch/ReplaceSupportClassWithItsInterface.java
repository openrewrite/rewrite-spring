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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceSupportClassWithItsInterface extends Recipe {

    @Override
    public String getDisplayName() {
        return "Transform classes that extend a given Class to implement the given Interface instead";
    }

    @Override
    public String getDescription() {
        return "As of Spring-Batch 5.0 Listeners has default methods (made possible by a Java 8 baseline) and can be " +
                "implemented directly without the need for this adapter.";
    }

    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully-qualified class name to be replaced.",
            example = "org.springframework.batch.core.listener.JobExecutionListenerSupport")
    String fullyQualifiedClassName;

    @Option(displayName = "Fully Qualified Interface Name",
            description = "A fully-qualified Interface name to replace by.",
            example = "org.springframework.batch.core.JobExecutionListener")
    String fullyQualifiedInterfaceName;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return Preconditions.check(new UsesType<>(fullyQualifiedClassName, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                            ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() != null
                        && TypeUtils.isOfClassType(cd.getExtends().getType(), fullyQualifiedClassName)) {
                    cd = cd.withExtends(null);
                    updateCursor(cd);
                    // This is an interesting one... JobExecutionListenerSupport implements
                    // JobExecutionListener
                    // remove the super type from the class type to prevent a stack-overflow
                    // exception when the JavaTemplate visits class type.
                    JavaType.Class type = (JavaType.Class) cd.getType();
                    if (type != null) {
                        cd = cd.withType(type.withSupertype(null));
                        updateCursor(cd);
                    }

                    cd = JavaTemplate
                        .builder(JavaType.ShallowClass.build(fullyQualifiedInterfaceName).getClassName())
                        .imports(fullyQualifiedInterfaceName)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5", "spring-batch-infrastructure-5"))
                        .build()
                        .apply(
                            getCursor(),
                            cd.getCoordinates().addImplementsClause()
                        );
                    cd = (J.ClassDeclaration) new RemoveSuperStatementVisitor().visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                    maybeRemoveImport(fullyQualifiedClassName);
                    maybeAddImport(fullyQualifiedInterfaceName);
                }
                return cd;
            }
        });
    }

    class RemoveSuperStatementVisitor extends JavaIsoVisitor<ExecutionContext> {
        final MethodMatcher wm = new MethodMatcher(fullyQualifiedClassName + " *(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                        ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (wm.matches(method.getMethodType())) {
                //noinspection DataFlowIssue
                return null;
            }
            return mi;
        }
    }
}
