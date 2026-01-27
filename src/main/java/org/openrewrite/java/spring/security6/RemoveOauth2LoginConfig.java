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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.search.UsesMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RemoveOauth2LoginConfig extends Recipe {
    private static final MethodMatcher O_AUTH_2_LOGIN_MATCHER = new MethodMatcher(
            "org.springframework.security.config.annotation.web.builders.HttpSecurity oauth2Login()"
    );

    @Getter
    final String displayName = "Remove unneeded `oauth2Login` config when upgrading to Spring Security 6";

    @Getter
    final String description = "`oauth2Login()` is a Spring Security feature that allows users to authenticate with an OAuth2 or OpenID " +
            "Connect 1.0 provider. When a user is authenticated using this feature, they are granted a set of " +
            "authorities that determines what actions they are allowed to perform within the application.\n" +
            "\n" +
            "In Spring Security 5, the default authority given to a user authenticated with an OAuth2 or OpenID " +
            "Connect 1.0 provider via `oauth2Login()` is `ROLE_USER`. This means that the user is allowed to access " +
            "the application's resources as a regular user.\n" +
            "\n" +
            "However, in Spring Security 6, the default authority given to a user authenticated with an OAuth2 " +
            "provider is `OAUTH2_USER`, and the default authority given to a user authenticated with an OpenID " +
            "Connect 1.0 provider is `OIDC_USER`. These authorities are more specific and allow for better " +
            "customization of the user's permissions within the application.\n" +
            "\n" +
            "If you are upgrading to Spring Security 6 and you have previously configured a " +
            "`GrantedAuthoritiesMapper` to handle the authorities of users authenticated via `oauth2Login()`, you can " +
            "remove it completely as the new default authorities should be sufficient.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(8);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<String> methods = new ArrayList<>();
        methods.add("org.springframework.security.config.annotation.web.builders.HttpSecurity oauth2Login()");
        methods.add("org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer userInfoEndpoint()");
        methods.add("org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer$UserInfoEndpointConfig userAuthoritiesMapper(org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper)");
        return Preconditions.check(new UsesMethod<>(O_AUTH_2_LOGIN_MATCHER), new RemoveMethodInvocationsVisitor(methods));
    }
}
