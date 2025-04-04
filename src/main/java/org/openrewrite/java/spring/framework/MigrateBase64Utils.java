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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class MigrateBase64Utils extends Recipe {

    private static final String SPRING_BASE_64_UTILS = "org.springframework.util.Base64Utils";
    private static final MethodMatcher ANY_BASE64UTILS = new MethodMatcher(SPRING_BASE_64_UTILS + " *(..)");

    private static final MethodMatcher ENCODE = new MethodMatcher(SPRING_BASE_64_UTILS + " encode(byte[])");
    private static final MethodMatcher DECODE = new MethodMatcher(SPRING_BASE_64_UTILS + " decode(byte[])");
    private static final MethodMatcher ENCODE_TO_STRING = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToString(byte[])");
    private static final MethodMatcher DECODE_FROM_STRING = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromString(String)");

    private static final MethodMatcher ENCODE_URL_SAFE = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeUrlSafe(byte[])");
    private static final MethodMatcher DECODE_URL_SAFE = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeUrlSafe(byte[])");
    private static final MethodMatcher ENCODE_TO_URL_SAFE_STRING = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToUrlSafeString(byte[])");
    private static final MethodMatcher DECODE_FROM_URL_SAFE_STRING = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromUrlSafeString(String)");

    @Override
    public String getDisplayName() {
        return "Migrate `org.springframework.util.Base64Utils` to `java.io.Base64`";
    }

    @Override
    public String getDescription() {
        return "Replaces usages of deprecated `org.springframework.util.Base64Utils` with `java.util.Base64`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ANY_BASE64UTILS), new JavaIsoVisitor<ExecutionContext>() {

            private Map<MethodMatcher, JavaTemplate.Builder> mappings() {
                Map<MethodMatcher, JavaTemplate.Builder> mappings = new HashMap<>();
                mappings.put(ENCODE, JavaTemplate.builder("Base64.getEncoder().encode(#{anyArray(byte)})"));
                mappings.put(DECODE, JavaTemplate.builder("Base64.getDecoder().decode(#{anyArray(byte)})"));
                mappings.put(ENCODE_TO_STRING, JavaTemplate.builder("Base64.getEncoder().encodeToString(#{anyArray(byte)})"));
                mappings.put(DECODE_FROM_STRING, JavaTemplate.builder("Base64.getDecoder().decode(#{any(String)})"));
                mappings.put(ENCODE_URL_SAFE, JavaTemplate.builder("Base64.getUrlEncoder().encode(#{anyArray(byte)})"));
                mappings.put(DECODE_URL_SAFE, JavaTemplate.builder("Base64.getUrlDecoder().decode(#{anyArray(byte)})"));
                mappings.put(ENCODE_TO_URL_SAFE_STRING, JavaTemplate.builder("Base64.getUrlEncoder().encodeToString(#{anyArray(byte)})"));
                mappings.put(DECODE_FROM_URL_SAFE_STRING, JavaTemplate.builder("Base64.getUrlDecoder().decode(#{any(String)})"));
                return mappings;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (ANY_BASE64UTILS.matches(m)) {
                    for (Map.Entry<MethodMatcher, JavaTemplate.Builder> entry : mappings().entrySet()) {
                        if (entry.getKey().matches(m)) {
                            maybeAddImport("java.util.Base64");
                            maybeRemoveImport(SPRING_BASE_64_UTILS);
                            return entry.getValue()
                                    .imports("java.util.Base64").build()
                                    .apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                        }
                    }
                }
                return m;
            }
        });
    }

}
