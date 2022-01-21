/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenVisitor;

/**
 * Migration for Spring Boot 2.4 to 2.5
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.5-Release-Notes#secure-info-endpoint">Secure Info Endpoint</a>
 */
@Incubating(since = "4.16.0")
public class SecureInfoEndpoint extends Recipe {

    private final boolean foundActuator = false;
    private final boolean foundSpringSecurity = false;
    private final boolean foundCustomSecurityConfiguration = false;

    @Override
    public String getDisplayName() {
        return "The `/info` actuator endpoint is no longer exposed over the web by default.";
    }

    @Override
    public String getDescription() {

        String description = getDisplayName();

        if( foundActuator == true &&
            foundSpringSecurity == false &&
            foundCustomSecurityConfiguration == false) {
            // Description with only actuator on classpath
            description =
                    "The scan found `org.springframework.boot:spring-boot-actuator` on the classpath.\n" +
                    "The actuator `/info` endpoint is no longer exposed over the web by default.\n" +
                    "If the `/info` endpoint needs to be accessible over the web you'll need to declare it as exposed over web.\n" +
                    "See [Exposing Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.exposing).\n" +
                    "Neither Spring Security nor a security configuration was found. You should take care to secure HTTP endpoints in the same way that you would any other sensitive URL. See [Securing HTTP Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.security) for further information.\n" +
                    "\n" +
                    "If you used the `/info` endpoint for health-checks you might consider using the `/health` endpoint instead.";
        } else if(foundActuator == true &&
                foundSpringSecurity == true &&
                foundCustomSecurityConfiguration == false) {
            // Description with actuator and security on classpath without custom security configuration
            description = 
                    "The scan found `org.springframework.boot:spring-boot-actuator` and `org.springframework.security:spring-security-core-*` on the classpath but no custom security configuration.\n" +
                    "\n" +
                    "The actuator `/info` endpoint is no longer exposed over the web by default.\n" +
                    "If the `/info` endpoint needs to be accessible over the web you'll need to declare it as exposed over web.\n" +
                    "See [Exposing Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.exposing).\n" +
                    "\n" +
                    "No security configuration was found and the `/info` endpoint now requires authenticated access by default using basic authentication with the default user named `user` and the random password that's logged at startup, see [Security](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#features.security)  for further information.\n" +
                    "\n" +
                    "If you want to change the authentication behaviour you can provide a custom security config for the `/info` endpoint.\n" +
                    "This also allows you to preserve the old behaviour by deactivating authentication in the custom security config.\n" +
                    "See [Securing HTTP Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.security) for further information.\n" +
                    "\n" +
                    "If you used the `/info` endpoint for health-checks you might consider using the `/health` endpoint instead.\n";
        } else if(foundActuator == true &&
                foundSpringSecurity == true &&
                foundCustomSecurityConfiguration == true) {
            // Description with actuator and security on classpath with custom security configuration
            description = 
                    "The scan found `org.springframework.boot:spring-boot-actuator` and `org.springframework.security:spring-security-core-*` on the classpath and custom security configuration(s) in these classes [list of security configurations].\n" +
                    "The actuator `/info` endpoint is no longer exposed over the web by default.\n" +
                    "If the `/info` endpoint needs to be accessible over the web you'll need to declare it as exposed over web.\n" +
                    "See [Exposing Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.exposing).\n" +
                    "Spring security and a custom security configuration was found. You should take care to provide a security configuration for the `/info` endpoint.\n" +
                    "See [Securing HTTP Endpoints](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#actuator.endpoints.security) for further information.\n" +
                    "\n" +
                    "If you used the `/info` endpoint for health-checks you might consider using the `/health` endpoint instead.";
        }
        return description;
    }


    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {

        // TODO: Maven: Check if org.springframework.boot:spring-boot-actuator and org.springframework.security:spring-security-web exist on classpath
        // TODO: Java: find custom security configuration, a bean like `SecurityFilterChain securityFilterChain(HttpSecurity http)`, the return type should be sufficient as indicator

        return new MavenVisitor() {

        };
    }
}
