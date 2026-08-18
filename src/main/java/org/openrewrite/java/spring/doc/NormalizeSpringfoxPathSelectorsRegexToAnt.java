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
package org.openrewrite.java.spring.doc;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
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

import java.util.regex.Pattern;

public class NormalizeSpringfoxPathSelectorsRegexToAnt extends Recipe {

    private static final MethodMatcher PATH_SELECTORS_REGEX = new MethodMatcher("springfox.documentation.builders.PathSelectors regex(java.lang.String)", true);
    private static final Pattern SAFE_LITERAL_SEGMENTS = Pattern.compile("(/[A-Za-z0-9_\\-]+)*/?");

    @Getter
    final String displayName = "Rewrite safe `PathSelectors.regex(...)` calls as `PathSelectors.ant(...)`";

    @Getter
    final String description = "Springdoc's `GroupedOpenApi.pathsToMatch(...)` accepts Ant-style patterns, not Java regex. " +
            "This recipe rewrites `PathSelectors.regex(...)` calls whose literal argument is a literal path prefix " +
            "followed by `.*` (optionally anchored with `^`/`$`) into the equivalent `PathSelectors.ant(...)` call, " +
            "so downstream Docket-to-GroupedOpenApi migration can translate the path. " +
            "Regex patterns that use metacharacters, alternation, or character classes are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(PATH_SELECTORS_REGEX), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!PATH_SELECTORS_REGEX.matches(mi) || mi.getArguments().size() != 1) {
                    return mi;
                }
                if (!(mi.getArguments().get(0) instanceof J.Literal)) {
                    return mi;
                }
                Object value = ((J.Literal) mi.getArguments().get(0)).getValue();
                if (!(value instanceof String)) {
                    return mi;
                }
                String ant = toAntIfSafe((String) value);
                if (ant == null) {
                    return mi;
                }
                return JavaTemplate.builder("PathSelectors.ant(\"" + ant + "\")")
                        .imports("springfox.documentation.builders.PathSelectors")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "springfox-spi", "springfox-core", "springfox-spring-web"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace());
            }
        });
    }

    static @Nullable String toAntIfSafe(String regex) {
        String r = regex;
        if (r.startsWith("^")) {
            r = r.substring(1);
        }
        if (r.endsWith("$")) {
            r = r.substring(0, r.length() - 1);
        }
        if (!r.endsWith(".*")) {
            return null;
        }
        String prefix = r.substring(0, r.length() - 2);
        if (prefix.isEmpty() || "/".equals(prefix)) {
            return "/**";
        }
        if (!prefix.endsWith("/")) {
            return null;
        }
        if (!SAFE_LITERAL_SEGMENTS.matcher(prefix).matches()) {
            return null;
        }
        return prefix + "**";
    }
}
