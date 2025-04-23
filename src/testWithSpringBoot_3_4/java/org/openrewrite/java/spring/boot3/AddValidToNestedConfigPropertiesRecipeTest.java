package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddValidToNestedConfigPropertiesRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddValidToNestedConfigPropertiesRecipe())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot", "spring-context", "jakarta.validation-api")
            .dependsOn(
              // language=java
              """
                package com.example.demo;

                import jakarta.validation.constraints.Min;

                public class NestedProperties {
                    @Min(1)
                    private int count;

                    public int getCount() {
                        return count;
                    }

                    public void setCount(int count) {
                        this.count = count;
                    }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void addValidToNestedProperties() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {

                  private String name;

                  private NestedProperties nested;
              }
              """,
            """
              package com.example.demo;

              import jakarta.validation.Valid;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {

                  private String name;

                  @Valid
                  private NestedProperties nested;
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidToSimpleTypes() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;
              import jakarta.validation.constraints.NotNull;
              import java.util.List;
              import java.util.Map;

              @ConfigurationProperties("app")
              @Validated
              public class SimpleProperties {
                  @NotNull
                  private String name;
                  private int count;
                  private List<String> items;
                  private Map<String, String> mappings;
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidIfAlreadyPresent() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;
              import jakarta.validation.Valid;

              @ConfigurationProperties("app")
              @Validated
              public class AlreadyValidatedProperties {

                  @Valid
                  private NestedProperties nested;
              }
              """
          )
        );
    }

    @Test
    void ignoreNonConfigurationPropertiesClasses() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.validation.annotation.Validated;

              @Validated
              public class RegularClass {
                  private NestedProperties nested;

                  public NestedProperties getNested() {
                      return nested;
                  }

                  public void setNested(NestedProperties nested) {
                      this.nested = nested;
                  }
              }
              """
          )
        );
    }
}
