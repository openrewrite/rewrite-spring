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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateConfigurationPropertiesBindingPostProcessorValidatorBeanNameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateConfigurationPropertiesBindingPostProcessorValidatorBeanName())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot", "spring-beans", "spring-core", "spring-context"));
    }

    @DocumentExample
    @Test
    void updateFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;

              class Test {
                  static void method() {
                      String value = ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;
                  }
              }
              """,
            """
              import org.springframework.boot.context.properties.EnableConfigurationProperties;

              class Test {
                  static void method() {
                      String value = EnableConfigurationProperties.VALIDATOR_BEAN_NAME;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateStaticConstant() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import static org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;

              class Test {
                  static void method() {
                      String value = VALIDATOR_BEAN_NAME;
                  }
              }
              """,
            """
              import static org.springframework.boot.context.properties.EnableConfigurationProperties.VALIDATOR_BEAN_NAME;

              class Test {
                  static void method() {
                      String value = VALIDATOR_BEAN_NAME;
                  }
              }
              """
          )
        );
    }

    @Test
    void updateFullyQualifiedTarget() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  static void method() {
                      String value = org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      String value = org.springframework.boot.context.properties.EnableConfigurationProperties.VALIDATOR_BEAN_NAME;
                  }
              }
              """
          )
        );
    }
}
