package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.FlywayMigration;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class FlywayMigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-boot"))
          .recipe(new FlywayMigration());
    }

    @Test
    void sample() {
        rewriteRun(
          java(
            """
              import flyway.NotificationBeanMigration;
              import org.flywaydb.core.api.configuration.FluentConfiguration;
              import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              public class MigrationConfiguration implements FlywayConfigurationCustomizer {
                  @Bean
                  public NotificationBeanMigration beanMigration() {
                      return new NotificationBeanMigration(null);
                  }

                  @Override
                  public void customize(FluentConfiguration configuration) {
                      // add callback
                      configuration.callbacks(new MyCallback());

                      // add java migration
                      configuration.javaMigrations(new MyJavaMigration());

                      // others
                      configuration.resolvers(new FlywayConfigurationResolver());
                  }
              }
              """
          )
        );
    }

    @Test
    void sample2() {
        rewriteRun(
          java(
            """
              import org.flywaydb.core.api.callback.Callback;
              import org.flywaydb.core.api.callback.Context;
              import org.flywaydb.core.api.callback.Event;
              import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Profile;


              @Profile("dev")
              @Configuration
              public class DevDbDataInitFlywayConfig {

                  @Bean
                  public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
                      return configuration -> {
                          // add callbacks
                          configuration.callbacks(new DevDbDataInitFlywayCallback());
                      };
                  }

                  public class DevDbDataInitFlywayCallback implements Callback {
                      @Override
                      public boolean canHandleInTransaction(Event event, Context context) {
                          return false;
                      }

                      @Override
                      public void handle(Event arg0, Context arg1) {
                      }

                      @Override
                      public String getCallbackName() {
                          return "dev";
                      }

                      @Override
                      public boolean supports(Event event, Context context) {
                          return event == Event.AFTER_MIGRATE;
                      }
                  }
              }
              """
          )
        );
    }

}
