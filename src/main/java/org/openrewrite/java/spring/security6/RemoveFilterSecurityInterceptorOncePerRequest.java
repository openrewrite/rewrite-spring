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
package org.openrewrite.java.spring.security6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.RemoveMethodInvocationsVisitor;

import java.util.ArrayList;
import java.util.List;

public class RemoveFilterSecurityInterceptorOncePerRequest extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove unnecessary `filterSecurityInterceptorOncePerRequest(false)` when upgrading to Spring Security 6";
    }

    @Override
    public String getDescription() {
        return "In Spring Security 6.0, `<http>` defaults `authorizeRequests#filterSecurityInterceptorOncePerRequest` to false." +
               " So, to complete migration, any defaults values can be removed.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        List<String> methods = new ArrayList<>();
        methods.add("org.springframework.security.config.annotation.web.configurers.AbstractInterceptUrlConfigurer.AbstractInterceptUrlRegistry filterSecurityInterceptorOncePerRequest(boolean)");
        return new RemoveMethodInvocationsVisitor(methods);
    }
}
