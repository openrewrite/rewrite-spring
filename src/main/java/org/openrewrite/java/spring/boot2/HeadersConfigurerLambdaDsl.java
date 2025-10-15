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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class HeadersConfigurerLambdaDsl extends Recipe {

    private static final String FQN_HEADERS_CONFIGURER = "org.springframework.security.config.annotation.web.configurers.HeadersConfigurer";

    private static final Collection<String> APPLICABLE_METHOD_NAMES = Arrays.asList(
            "contentTypeOptions", "xssProtection", "cacheControl", "httpStrictTransportSecurity", "frameOptions",
            "contentSecurityPolicy", "referrerPolicy", "permissionsPolicy", "crossOriginOpenerPolicy",
            "crossOriginEmbedderPolicy", "crossOriginResourcePolicy");

    private static final Map<String, String> ARG_REPLACEMENTS;
    static {
        ARG_REPLACEMENTS = new HashMap<String, String>();
        ARG_REPLACEMENTS.put("contentSecurityPolicy", "policyDirectives");
        ARG_REPLACEMENTS.put("referrerPolicy", "policy");
    }

    @Override
    public String getDisplayName() {
        return "Convert `HeadersConfigurer` chained calls into Lambda DSL";
    }

    @Override
    public String getDescription() {
        return "Converts `HeadersConfigurer` chained call from Spring Security pre 5.2.x into new lambda DSL style calls and removes `and()` methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(FQN_HEADERS_CONFIGURER, true),
                new ConvertToSecurityDslVisitor<>(FQN_HEADERS_CONFIGURER, APPLICABLE_METHOD_NAMES, ARG_REPLACEMENTS)
        );
    }

}
