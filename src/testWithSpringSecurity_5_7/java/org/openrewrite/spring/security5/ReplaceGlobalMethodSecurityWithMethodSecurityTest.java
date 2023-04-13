package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurity;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

public class ReplaceGlobalMethodSecurityWithMethodSecurityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceGlobalMethodSecurityWithMethodSecurity())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-security-config-5.8.+"));
    }

    @Test
    void replaceWithPrePostEnabled() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(prePostEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity
              public class config {
              }
              """
          )
        );
    }

    @Test
    void replaceWithNotPrePostEnabled() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(securedEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
              public class config {
              }
              """
          )
        );
    }

    @Test
    void removeUseAuthorizationManagerAttribute() {
        rewriteRun(
          spec -> spec.recipe(
            Environment.builder()
              .scanRuntimeClasspath()
              .build()
              .activateRecipes("org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurityXml")
          ),
          //language=xml
          xml(
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
                <global-method-security pre-post-enabled="true"/>
              </b:beans>
              """,
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
                <method-security/>
              </b:beans>
              """
          )
        );
    }
}
