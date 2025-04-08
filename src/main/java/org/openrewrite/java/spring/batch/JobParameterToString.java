/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.batch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class JobParameterToString extends Recipe {

    private static final MethodMatcher JOB_PARAMETER_TO_STRING_MATCHER = new MethodMatcher("org.springframework.batch.core.JobParameter toString()");

    @Override
    public String getDisplayName() {
        return "Migration invocation of JobParameter.toString to JobParameter.getValue.toString";
    }

    @Override
    public String getDescription() {
        return "JobParameter.toString() logic is quite different in spring batch 5, need take JobParameter.getValue.toString replace the JobParameter.toString.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(JOB_PARAMETER_TO_STRING_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);
                        if (JOB_PARAMETER_TO_STRING_MATCHER.matches(method)) {
                            return JavaTemplate.builder("#{any()}.getValue().toString()")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-4.3.+"))
                                    .build().apply(getCursor(), method.getCoordinates().replace(), method.getSelect());
                        }
                        return method;
                    }
                });
    }
}
