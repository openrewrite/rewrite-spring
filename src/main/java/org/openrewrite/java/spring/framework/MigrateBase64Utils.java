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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class MigrateBase64Utils extends Recipe {

    private static final String SPRING_BASE_64_UTILS = "org.springframework.util.Base64Utils";

    @Override
    public String getDisplayName() {
        return "Migrate `org.springframework.util.Base64Utils` to `java.io.Base64`";
    }

    @Override
    public String getDescription() {
        return "`org.springframework.util.Base64Utils` was deprecated, in favor of `java.util.Base64`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SPRING_BASE_64_UTILS, false), new UpdateDeprecatedMethods());
    }

    private static class UpdateDeprecatedMethods extends JavaIsoVisitor<ExecutionContext> {

        private static final String JAVA_UTIL_BASE_64 = "java.util.Base64";

        private final Map<MethodMatcher, JavaTemplate> SPRING_TO_JDK_MAPPINGS = new HashMap<>();

        UpdateDeprecatedMethods() {
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " encode(byte[])"), JavaTemplate.builder("Base64.getEncoder().encode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " decode(byte[])"), JavaTemplate.builder("Base64.getDecoder().decode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToString(byte[])"), JavaTemplate.builder("Base64.getEncoder().encodeToString(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromString(String)"), JavaTemplate.builder("Base64.getDecoder().decode(#{any(String)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " encodeUrlSafe(byte[])"), JavaTemplate.builder("Base64.getUrlEncoder().encode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " decodeUrlSafe(byte[])"), JavaTemplate.builder("Base64.getUrlDecoder().decode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToUrlSafeString(byte[])"), JavaTemplate.builder("Base64.getUrlEncoder().encodeToString(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build());
            SPRING_TO_JDK_MAPPINGS.put(new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromUrlSafeString(String)"), JavaTemplate.builder("Base64.getUrlDecoder().decode(#{any(String)})").imports(JAVA_UTIL_BASE_64).build());
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            for (Map.Entry<MethodMatcher, JavaTemplate> entry : SPRING_TO_JDK_MAPPINGS.entrySet()) {
                if (entry.getKey().matches(m)) {
                    m = entry.getValue().apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
            }
            maybeAddImport(JAVA_UTIL_BASE_64);
            maybeRemoveImport(SPRING_BASE_64_UTILS);
            return m;
        }
    }

}
