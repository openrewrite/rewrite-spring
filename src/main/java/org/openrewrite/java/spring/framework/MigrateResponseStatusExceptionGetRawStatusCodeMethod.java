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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;

public class MigrateResponseStatusExceptionGetRawStatusCodeMethod extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate `ResponseStatusException#getRawStatusCode()` to `getStatusCode().value()`";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Framework 5.3's `ResponseStatusException` method `getRawStatusCode()` to Spring Framework 6's `getStatusCode().value()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Traits.methodAccess("org.springframework.web.server.ResponseStatusException getRawStatusCode()")
                .asVisitor((mc, ctx) -> {
                    MethodCall tree = mc.getTree();
                    if (tree instanceof J.MethodInvocation) {
                        return JavaTemplate.builder("#{any()}.getStatusCode().value()")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-core-6", "spring-beans-6", "spring-web-6"))
                                .build().apply(mc.getCursor(), tree.getCoordinates().replace(), ((J.MethodInvocation) tree).getSelect());
                    }
                    return tree;
                });
    }
}
