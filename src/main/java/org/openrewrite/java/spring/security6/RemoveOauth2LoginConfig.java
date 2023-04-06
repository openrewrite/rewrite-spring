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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;

public class RemoveOauth2LoginConfig extends Recipe {

    private static final MethodMatcher O_AUTH_2_LOGIN_MATCHER = new MethodMatcher(
        "org.springframework.security.config.annotation.web.builders.HttpSecurity oauth2Login()"
    );

    private static final MethodMatcher USER_INFO_ENDPOINT_MATCHER = new MethodMatcher(
        "org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer userInfoEndpoint()"
    );

    private static final MethodMatcher USER_AUTHORITIES_MAPPER_MATCHER = new MethodMatcher(
        "org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer$UserInfoEndpointConfig userAuthoritiesMapper(org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper)"
    );


    @Override
    public String getDisplayName() {
        return "Remove unneeded `oauth2Login` config when upgrading to Spring Security 6";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "`oauth2Login()` is a Spring Security feature that allows users to authenticate with an OAuth2 or OpenID" +
               " Connect 1.0 provider. When a user is authenticated using this feature, they are granted a set of " +
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
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(8);
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(O_AUTH_2_LOGIN_MATCHER);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext executionContext) {
                Cursor parent = getCursor().dropParentUntil(p -> p instanceof J.Assignment ||
                                                                 p instanceof J.VariableDeclarations.NamedVariable ||
                                                                 p instanceof J.Return ||
                                                                 p instanceof J.Block ||
                                                                 p instanceof J.CompilationUnit);
                boolean isAssignment = parent.getValue() instanceof J.Assignment ||
                                       parent.getValue() instanceof J.VariableDeclarations.NamedVariable ||
                                       parent.getValue() instanceof J.Return;
                if (isAssignment) {
                    // don't rewrite if this method invocation is assigned to a variable.
                    return method;
                }

                if (USER_AUTHORITIES_MAPPER_MATCHER.matches(method) ||
                    O_AUTH_2_LOGIN_MATCHER.matches(method) ||
                    USER_INFO_ENDPOINT_MATCHER.matches(method)) {

                    if (method.getSelect() instanceof J.Identifier) {
                        // remove this statement
                        return null;
                    } else if (method.getSelect() instanceof J.MethodInvocation) {
                        // update the statement
                        doAfterVisit(this);
                        return method.getSelect().withPrefix(method.getPrefix());
                    }
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        };
    }
}
