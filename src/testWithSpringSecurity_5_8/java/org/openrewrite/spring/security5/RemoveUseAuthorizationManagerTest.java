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
