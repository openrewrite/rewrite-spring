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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateHttpHeadersMultiValueMapMethods extends Recipe {

    private static final String HTTP_HEADERS = "org.springframework.http.HttpHeaders";

    @Getter
    final String displayName = "Migrate `HttpHeaders` methods removed when `MultiValueMap` contract was dropped";

    @Getter
    final String description = "Spring Framework 7.0 changed `HttpHeaders` to no longer implement `MultiValueMap`. " +
            "This recipe replaces removed inherited method calls: " +
            "`containsKey()` with `containsHeader()`, `keySet()` with `headerNames()`, " +
            "and `entrySet()` with `headerSet()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(HTTP_HEADERS, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (mi.getSelect() != null &&
                            TypeUtils.isOfClassType(mi.getSelect().getType(), HTTP_HEADERS)) {
                            String name = mi.getSimpleName();
                            if ("containsKey".equals(name)) {
                                return mi.withName(mi.getName().withSimpleName("containsHeader"));
                            }
                            if ("keySet".equals(name)) {
                                return mi.withName(mi.getName().withSimpleName("headerNames"));
                            }
                            if ("entrySet".equals(name)) {
                                return mi.withName(mi.getName().withSimpleName("headerSet"));
                            }
                        }
                        return mi;
                    }
                }
        );
    }
}
