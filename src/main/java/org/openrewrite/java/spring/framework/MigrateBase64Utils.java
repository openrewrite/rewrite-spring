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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateBase64Utils extends Recipe {

    private static final String SPRING_BASE_64_UTILS = "org.springframework.util.Base64Utils";

    private static final String JAVA_UTIL_BASE_64 = "java.util.Base64";

    private static final MethodMatcher springEncode = new MethodMatcher(SPRING_BASE_64_UTILS + " encode(byte[])");

    private static final MethodMatcher springDecode = new MethodMatcher(SPRING_BASE_64_UTILS + " decode(byte[])");

    private static final MethodMatcher springEncodeUrlSafe = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeUrlSafe(byte[])");

    private static final MethodMatcher springDecodeUrlSafe = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeUrlSafe(byte[])");

    private static final MethodMatcher springEncodeToString = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToString(byte[])");

    private static final MethodMatcher springDecodeFromString = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromString(String)");

    private static final MethodMatcher springEncodeToUrlSafeString = new MethodMatcher(SPRING_BASE_64_UTILS + " encodeToUrlSafeString(byte[])");

    private static final MethodMatcher springDecodeFromUrlSafeString = new MethodMatcher(SPRING_BASE_64_UTILS + " decodeFromUrlSafeString(String)");

    private static final JavaTemplate encode = JavaTemplate.builder("Base64.getEncoder().encode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate decode = JavaTemplate.builder("Base64.getDecoder().decode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate encodeToString = JavaTemplate.builder("Base64.getEncoder().encodeToString(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate decodeFromString = JavaTemplate.builder("Base64.getDecoder().decode(#{any(String)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate encodeUrlSafe = JavaTemplate.builder("Base64.getUrlEncoder().encode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate decodeUrlSafe = JavaTemplate.builder("Base64.getUrlDecoder().decode(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate encodeToUrlSafeString = JavaTemplate.builder("Base64.getUrlEncoder().encodeToString(#{anyArray(byte)})").imports(JAVA_UTIL_BASE_64).build();

    private static final JavaTemplate decodeFromUrlSafeString = JavaTemplate.builder("Base64.getUrlDecoder().decode(#{any(String)})").imports(JAVA_UTIL_BASE_64).build();

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
        return Preconditions.check(new UsesType<>(SPRING_BASE_64_UTILS, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (springEncode.matches(m)) {
                    m = encode.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springDecode.matches(m)) {
                    m = decode.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springEncodeUrlSafe.matches(m)) {
                    m = encodeUrlSafe.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springDecodeUrlSafe.matches(m)) {
                    m = decodeUrlSafe.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springEncodeToString.matches(m)) {
                    m = encodeToString.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springDecodeFromString.matches(m)) {
                    m = decodeFromString.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springEncodeToUrlSafeString.matches(m)) {
                    m = encodeToUrlSafeString.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                if (springDecodeFromUrlSafeString.matches(m)) {
                    m = decodeFromUrlSafeString.apply(updateCursor(m), m.getCoordinates().replace(), method.getArguments().get(0));
                }
                maybeAddImport(JAVA_UTIL_BASE_64);
                maybeRemoveImport(SPRING_BASE_64_UTILS);
                return m;
            }
        });
    }

}
