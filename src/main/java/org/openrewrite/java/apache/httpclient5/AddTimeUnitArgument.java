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
package org.openrewrite.java.apache.httpclient5;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.concurrent.TimeUnit;


@Value
@EqualsAndHashCode(callSuper = true)
public class AddTimeUnitArgument extends Recipe {

    @Option(displayName = "Method pattern",
            description = "A method pattern that is used to find matching method invocations.",
            example = "org.apache.http.client.config.RequestConfig.Builder setConnectionRequestTimeout(int)")
    String methodPattern;

    @Option(displayName = "Time Unit",
            description = "The TimeUnit enum value we want to add to the method invocation. Defaults to `MILLISECONDS`.",
            example = "MILLISECONDS",
            required = false)
    @Nullable
    TimeUnit timeUnit;

    @Override
    public String getDisplayName() {
        return "Adds a TimeUnit argument to the matched method invocations";
    }

    @Override
    public String getDescription() {
        return "In Apache Http Client 5.x migration, we need to add a TimeUnit argument to the timeout and duration methods. " +
                "Previously in 4.x, all this methods were implicitly having the timeout or duration expressed in milliseconds, " +
                "but in 5.x we explicitly need to indicate the unit of the timeout or duration. So, by default it will add " +
                "`TimeUnit.MILLISECONDS`, but you can specify this as a parameter too if needed. Since all affected methods of " +
                "the Apache Http Client 5.x migration only have one integer/long argument, it will only apply with matched method " +
                "invocations of exactly one parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher matcher = new MethodMatcher(methodPattern);
            final JavaTemplate template = JavaTemplate.builder("#{any(long)}, TimeUnit.#{}")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpath("httpclient5", "httpcore5"))
                    .imports("java.util.concurrent.TimeUnit")
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (matcher.matches(method) && method.getArguments().size() == 1) {
                    m = template.apply(
                            updateCursor(m),
                            m.getCoordinates().replaceArguments(),
                            m.getArguments().get(0),
                            timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS
                    );
                    maybeAddImport("java.util.concurrent.TimeUnit");
                }
                return m;
            }
        };
    }
}
