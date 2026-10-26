/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.InJavaSourceSet;
import org.openrewrite.java.spring.table.ApiCalls;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindApiCalls extends Recipe {
    private static final MethodMatcher REST_TEMPLATE_CALL = new MethodMatcher("org.springframework.web.client.RestTemplate *For*(..)");
    private static final MethodMatcher REST_TEMPLATE_EXCHANGE = new MethodMatcher("org.springframework.web.client.RestTemplate exchange(*, org.springframework.http.HttpMethod, ..)");
    private static final MethodMatcher URI_CREATE = new MethodMatcher("java.net.URI create(String)");

    transient ApiCalls calls = new ApiCalls(this);

    String displayName = "Find HTTP API calls via `RestTemplate`";

    String description = "Find outbound HTTP API calls made via Spring's `RestTemplate` class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new InJavaSourceSet<>("main"), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (REST_TEMPLATE_CALL.matches(method)) {
                    String httpMethod = method.getSimpleName().substring(0, method.getSimpleName().indexOf("For")).toUpperCase();
                    return found(m, httpMethod, method.getArguments().get(0), ctx);
                }
                if (REST_TEMPLATE_EXCHANGE.matches(method)) {
                    return found(m, httpMethod(method.getArguments().get(1)), method.getArguments().get(0), ctx);
                }
                return m;
            }

            private J.MethodInvocation found(J.MethodInvocation m, String httpMethod, Expression uri, ExecutionContext ctx) {
                String uriValue = uriValue(uri);
                calls.insertRow(ctx, new ApiCalls.Row(
                        getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                        httpMethod,
                        uriValue
                ));
                return SearchResult.found(m, httpMethod + " " + uriValue);
            }

            private String uriValue(Expression uri) {
                if (uri instanceof J.Literal) {
                    return String.valueOf(((J.Literal) uri).getValue());
                }
                if (URI_CREATE.matches(uri)) {
                    return uriValue(((J.MethodInvocation) uri).getArguments().get(0));
                }
                return uri.printTrimmed(getCursor());
            }

            private String httpMethod(Expression httpMethod) {
                JavaType.Variable fieldType = httpMethod instanceof J.FieldAccess ?
                        ((J.FieldAccess) httpMethod).getName().getFieldType() :
                        httpMethod instanceof J.Identifier ? ((J.Identifier) httpMethod).getFieldType() : null;
                if (fieldType != null && fieldType.hasFlags(Flag.Static) &&
                        TypeUtils.isOfClassType(fieldType.getOwner(), "org.springframework.http.HttpMethod")) {
                    return fieldType.getName();
                }
                return "UNKNOWN";
            }
        });
    }
}
