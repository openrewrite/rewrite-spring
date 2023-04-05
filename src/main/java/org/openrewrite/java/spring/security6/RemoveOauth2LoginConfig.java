package org.openrewrite.java.spring.security6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.List;

public class RemoveOauth2LoginConfig extends Recipe {
    private static final MethodMatcher USER_AUTHORITIES_MAPPER_MATCHER = new MethodMatcher(
        "org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer userAuthoritiesMapper(org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper)"
    );


    @Override
    public String getDisplayName() {
        return "Remove unneeded `oauth2Login` config when upgrading to Spring Security 6";
    }

    @Override
    public String getDescription() {
        return "oauth2Login() is a Spring Security feature that allows users to authenticate with an OAuth2 or OpenID" +
               " Connect 1.0 provider. When a user is authenticated using this feature, they are granted a set of " +
               "authorities that determines what actions they are allowed to perform within the application.\n" +
               "\n" +
               "In Spring Security 5, the default authority given to a user authenticated with an OAuth2 or OpenID " +
               "Connect 1.0 provider via oauth2Login() is ROLE_USER. This means that the user is allowed to access " +
               "the application's resources as a regular user.\n" +
               "\n" +
               "However, in Spring Security 6, the default authority given to a user authenticated with an OAuth2 " +
               "provider is OAUTH2_USER, and the default authority given to a user authenticated with an OpenID " +
               "Connect 1.0 provider is OIDC_USER. These authorities are more specific and allow for better " +
               "customization of the user's permissions within the application.\n" +
               "\n" +
               "If you are upgrading to Spring Security 6 and you have previously configured a " +
               "GrantedAuthoritiesMapper to handle the authorities of users authenticated via oauth2Login(), you can " +
               "remove it completely as the new default authorities should be sufficient.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(8);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext executionContext) {
                block = super.visitBlock(block, executionContext);


                List<Statement> statements = block.getStatements();


                return block;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext executionContext) {

                if (USER_AUTHORITIES_MAPPER_MATCHER.matches(method)) {
                    return method;
                }
                return method;
            }
        };
    }
}
