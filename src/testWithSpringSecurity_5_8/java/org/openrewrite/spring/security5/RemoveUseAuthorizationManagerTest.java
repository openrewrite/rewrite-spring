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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

public class RemoveUseAuthorizationManagerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.java.spring.security6.RemoveUseAuthorizationManager"));
    }

    @Test
    void removeUseAuthorizationManagerAttribute() {
        rewriteRun(
          //language=xml
          xml(
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">

              	<b:import resource="classpath:org/springframework/security/config/websocket/controllers.xml"/>
              	<b:import resource="classpath:org/springframework/security/config/websocket/websocket.xml"/>

              	<websocket-message-broker use-authorization-manager="true">
              		<intercept-message pattern="/permitAll" type="MESSAGE" access="permitAll"/>
              		<intercept-message pattern="/**" access="denyAll"/>
              	</websocket-message-broker>

              </b:beans>
              """,
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">

              	<b:import resource="classpath:org/springframework/security/config/websocket/controllers.xml"/>
              	<b:import resource="classpath:org/springframework/security/config/websocket/websocket.xml"/>

              	<websocket-message-broker>
              		<intercept-message pattern="/permitAll" type="MESSAGE" access="permitAll"/>
              		<intercept-message pattern="/**" access="denyAll"/>
              	</websocket-message-broker>

              </b:beans>
              """
          )
        );
    }
}
