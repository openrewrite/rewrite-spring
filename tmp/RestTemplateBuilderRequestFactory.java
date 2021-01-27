/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

/**
 * https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.0-Migration-Guide#resttemplatebuilder
 */
@AutoConfigure
public class RestTemplateBuilderRequestFactory extends JavaRefactorVisitor {
    private static final MethodMatcher requestFactory = new MethodMatcher(
            "org.springframework.boot.web.client.RestTemplateBuilder requestFactory(org.springframework.http.client.ClientHttpRequestFactory)");

    @Override
    public boolean isIdempotent() {
        // not updating resolved method signature, so the method would
        // continue matching on subsequent cycles
        return false;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
        if (requestFactory.matches(method)) {
            m = m.withArgs(m.getArgs().withArgs(m.getArgs().getArgs().stream()
                    .map(arg -> new J.Lambda(
                            randomId(),
                            new J.Lambda.Parameters(randomId(), true, emptyList()),
                            new J.Lambda.Arrow(randomId(), Formatting.format(" ")),
                            arg.withPrefix(" "),
                            new JavaType.GenericTypeVariable(
                                    "java.util.function.Supplier",
                                    (JavaType.Class) arg.getType()),
                            arg.getFormatting())
                    )
                    .collect(toList())));
        }
        return m;
    }
}
