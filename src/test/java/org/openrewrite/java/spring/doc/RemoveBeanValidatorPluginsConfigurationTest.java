package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveBeanValidatorPluginsConfigurationTest implements RewriteTest {


    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveBeanValidatorPluginsConfiguration())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-context", "springfox-bean-validators"));
    }

    @DocumentExample
    @Test
    void removeImportWithBeanValidatorPluginsConfiguration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;
              import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;

              @Configuration
              @Import(BeanValidatorPluginsConfiguration.class)
              class ApplicationConfiguration {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;

              @Configuration
              class ApplicationConfiguration {}
              """
          )
        );
    }

    @Test
    void removeImportWithBeanValidatorPluginsConfigurationWhenSingleArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;
              import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;

              @Configuration
              @Import({BeanValidatorPluginsConfiguration.class})
              class ApplicationConfiguration {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;

              @Configuration
              class ApplicationConfiguration {}
              """
          )
        );
    }

    @Test
    void removeBeanValidatorPluginsConfigurationWhenMultipleArray() {
        //language=java
        rewriteRun(
          java(
            """
              class Some {}
              class SomeOther {}
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;
              import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;

              @Configuration
              @Import({Some.class, BeanValidatorPluginsConfiguration.class, SomeOther.class})
              class ApplicationConfiguration {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Import;

              @Configuration
              @Import({Some.class, SomeOther.class})
              class ApplicationConfiguration {}
              """
          )
        );
    }
}
