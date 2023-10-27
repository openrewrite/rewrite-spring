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
package org.openrewrite.java.spring.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.spring.table.ApiCalls;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.Objects;


@Value
@EqualsAndHashCode(callSuper = true)
public class FindApiCalls extends Recipe {
    transient ApiCalls calls = new ApiCalls(this);

    @Override
    public String getDisplayName() {
        return "Find API calls";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Find outbound HTTP API calls made via Spring's `RestTemplate` class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher restTemplateCall = new MethodMatcher("org.springframework.web.client.RestTemplate *For*(..)");
            final MethodMatcher restTemplateExchange = new MethodMatcher("org.springframework.web.client.RestTemplate exchange(String, ..)");
            final MethodMatcher webClientUri = new MethodMatcher("org.springframework.web.reactive.function.client.WebClient.UriSpec(String, ..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (restTemplateCall.matches(method)) {
                    String httpMethod = method.getSimpleName().substring(0, method.getSimpleName().indexOf("For")).toUpperCase();
                    Expression uri = method.getArguments().get(0);
                    String uriValue = uri instanceof J.Literal ?
                            Objects.toString(((J.Literal) uri).getValue()) :
                            uri.printTrimmed(getCursor());
                    m = SearchResult.found(m, httpMethod + " " + uriValue);
                    calls.insertRow(ctx, new ApiCalls.Row(
                            getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                            httpMethod,
                            uriValue
                    ));
                } else if (restTemplateExchange.matches(method)) {

                }
                return m;
            }
        };
    }
}
