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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

/**
 * Changes Depricated Hibernate validation constraints to their associated Javax validation variant.
 *
 * Then sets the 'javax-validation-exists' ExecutionContext value to True which allows the {@link MaybeAddJavaxValidationDependencies}
 * to add the associated dependencies
 */
public class ChangeDeprecatedHibernateValidationToJavax extends Recipe {

    private static List<String> HIBERNATE_TO_JAVAX_VALIDATION_CONSTRAINTS = Arrays.asList("NotEmpty", "NotBlank");

    @Override
    public String getDisplayName() {
        return "Change Deprecated Hibernate validation constraints to their associated javax variant";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangeDepricatedHibernateValidationToJavaxVisitor();
    }

    private class ChangeDepricatedHibernateValidationToJavaxVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            HIBERNATE_TO_JAVAX_VALIDATION_CONSTRAINTS.stream().forEach(t -> {
                if (!FindTypes.find(cu,"org.hibernate.validator.constraints." + t).isEmpty()) {
                    doAfterVisit(new ChangeType("org.hibernate.validator.constraints." + t, "javax.validation.constraints." + t));
                    executionContext.putMessage(MaybeAddJavaxValidationDependencies.JAVAX_VALIDATION_EXISTS, Boolean.TRUE);
                }
            });
            return super.visitCompilationUnit(cu, executionContext);
        }
    }
}
