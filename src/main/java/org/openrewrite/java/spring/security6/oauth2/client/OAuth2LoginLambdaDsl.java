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
package org.openrewrite.java.spring.security6.oauth2.client;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.boot2.ConvertToSecurityDslVisitor;

import java.util.Arrays;
import java.util.Collection;

@Value
@EqualsAndHashCode(callSuper = false)
public class OAuth2LoginLambdaDsl extends Recipe {
    private static final String FQN_OAUTH2_LOGIN_CONFIGURER = "org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer";

    private static final Collection<String> APPLICABLE_METHOD_NAMES = Arrays.asList("authorizationEndpoint", "redirectionEndpoint",
            "tokenEndpoint", "userInfoEndpoint");
    @Override
    public String getDisplayName() {
        return "Convert `OAuth2LoginConfigurer` chained calls into Lambda DSL";
    }

    @Override
    public String getDescription() {
        return "Converts `OAuth2LoginConfigurer` chained call from Spring Security pre 5.2.x into new lambda DSL style calls and removes `and()` methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(FQN_OAUTH2_LOGIN_CONFIGURER, true),
                new ConvertToSecurityDslVisitor<>(FQN_OAUTH2_LOGIN_CONFIGURER, APPLICABLE_METHOD_NAMES)
        );
    }
}
