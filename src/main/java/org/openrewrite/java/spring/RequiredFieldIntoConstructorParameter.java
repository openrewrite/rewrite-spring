/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;

public class RequiredFieldIntoConstructorParameter extends Recipe {

    @Override
    public String getDisplayName() {
        return "Required field into constructor parameter";
    }

    @Override
    public String getDescription() {
        return "The RequiredFieldIntoConstructorParameter recipe moves fields which have setters marked with the " +
                "@Required annotation to constructor parameters. It does not make changes when an existing constructor " +
                "exists, for inherited fields, or for classes annotated with Lombok constructors.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.beans.factory.annotation.Required", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    // TODO:
                });
    }
}
