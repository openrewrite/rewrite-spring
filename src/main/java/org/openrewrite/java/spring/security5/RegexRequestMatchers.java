/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.security5;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;

/// The arguments of a deprecated `regexMatchers`/`regexMatcher` call, as `new RegexRequestMatcher(..)` template code.
/// Passing the regular expressions on as `String` patterns would have them interpreted as MVC or Ant patterns instead.
@Value
class RegexRequestMatchers {
    static final String REGEX_REQUEST_MATCHER = "org.springframework.security.web.util.matcher.RegexRequestMatcher";
    private static final String HTTP_METHOD = "org.springframework.http.HttpMethod";

    int count;
    String template;
    Object[] parameters;

    /// @return `null` when the arguments can't be converted faithfully, such as a `String[]` of patterns,
    /// or an `HttpMethod` that is not a constant.
    static @Nullable RegexRequestMatchers from(List<Expression> arguments) {
        List<Expression> args = ListUtils.filter(arguments, arg -> !(arg instanceof J.Empty));
        Expression httpMethod = null;
        if (!args.isEmpty() && TypeUtils.isOfClassType(args.get(0).getType(), HTTP_METHOD)) {
            httpMethod = args.get(0);
            if (!isHttpMethodConstant(httpMethod)) {
                return null;
            }
            args = args.subList(1, args.size());
        }
        if (args.isEmpty() || !args.stream().allMatch(arg -> TypeUtils.isString(arg.getType()))) {
            return null;
        }

        String matcher = httpMethod == null ?
                "new RegexRequestMatcher(#{any(java.lang.String)}, null)" :
                "new RegexRequestMatcher(#{any(java.lang.String)}, #{any(" + HTTP_METHOD + ")}.name())";
        List<Object> parameters = new ArrayList<>();
        for (Expression pattern : args) {
            parameters.add(pattern);
            if (httpMethod != null) {
                parameters.add(httpMethod.withId(randomId()));
            }
        }
        return new RegexRequestMatchers(args.size(),
                args.stream().map(arg -> matcher).collect(joining(", ")),
                parameters.toArray());
    }

    JavaTemplate.Builder template(String code, ExecutionContext ctx) {
        return JavaTemplate.builder(String.format(code, template))
                .imports(REGEX_REQUEST_MATCHER)
                .javaParser(JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "spring-security-config-5.8", "spring-security-web-5.8", "spring-web-5"));
    }

    private static boolean isHttpMethodConstant(Expression expression) {
        JavaType.Variable variable = null;
        if (expression instanceof J.FieldAccess) {
            variable = ((J.FieldAccess) expression).getName().getFieldType();
        } else if (expression instanceof J.Identifier) {
            variable = ((J.Identifier) expression).getFieldType();
        }
        return variable != null && TypeUtils.isOfClassType(variable.getOwner(), HTTP_METHOD);
    }
}
