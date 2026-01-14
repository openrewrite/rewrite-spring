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
package org.openrewrite.java.spring.security6;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.boot2.ConvertToSecurityDslVisitor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

public class ApplyToWithLambdaDsl extends Recipe {

    private static final String FQN_ABSTRACT_CONFIGURED_SECURITY_BUILDER =
            "org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder";

    private static final Collection<String> APPLICABLE_METHOD_NAMES = singletonList("apply");

    private static final Map<String, String> ARG_REPLACEMENTS = new HashMap<String, String>() {{
        put("apply", null);
    }};

    private static final Map<String, String> METHOD_RENAMES = new HashMap<String, String>() {{
        put("apply", "with");
    }};

    @Getter
    final String displayName = "Convert `HttpSecurity::apply` chained calls into `HttpSecurity::with` Lambda DSL";

    @Getter
    final String description = "Converts `HttpSecurity::apply` chained call from Spring Security pre 6.2.x into new lambda DSL style calls and removes `and()` methods.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
          new UsesType<>(FQN_ABSTRACT_CONFIGURED_SECURITY_BUILDER, true),
          new ConvertToSecurityDslVisitor<>(FQN_ABSTRACT_CONFIGURED_SECURITY_BUILDER,
                  APPLICABLE_METHOD_NAMES, ARG_REPLACEMENTS, METHOD_RENAMES)
        );
    }
}
