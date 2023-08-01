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
package org.openrewrite.java.apache.httpclient4;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateDefaultHttpClient extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrates Deprecated DefaultHttpClient";
    }

    @Override
    public String getDescription() {
        return "Since DefaultHttpClient is deprecated, we need to change it to the CloseableHttpClient. " +
                "Only covering the default scenario with no custom HttpParams or ConnectionManager.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.http.impl.client.DefaultHttpClient", false), new JavaVisitor<ExecutionContext>() {
            MethodMatcher noArgsMatcher = new MethodMatcher("org.apache.http.impl.client.DefaultHttpClient <constructor>()");
            JavaTemplate noArgsTemplate = JavaTemplate.builder("HttpClients.createDefault()")
                    .javaParser(JavaParser.fromJavaVersion().classpath("httpclient"))
                    .imports("org.apache.http.impl.client.HttpClients")
                    .build();

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                if (noArgsMatcher.matches(newClass.getConstructorType())) {
                    maybeAddImport("org.apache.http.impl.client.HttpClients");
                    doAfterVisit(new ChangeType(
                            "org.apache.http.impl.client.DefaultHttpClient",
                            "org.apache.http.impl.client.CloseableHttpClient", true
                    ).getVisitor());
                    return noArgsTemplate.apply(getCursor(), newClass.getCoordinates().replace());
                }
                return super.visitNewClass(newClass, executionContext);
            }
        });
    }

}
