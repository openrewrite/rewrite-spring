/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.security7;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SecurityBuilderRemoveThrowsExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.security7.SecurityBuilderRemoveThrowsException")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-security-core-6",
              "spring-security-config-6.2",
              "spring-security-web-6.2",
              "spring-context-6"));
    }

    @DocumentExample
    /**
     * Spring Security 7 dropped {@code throws Exception} from the overridable
     * {@code AbstractConfiguredSecurityBuilder} template methods too, so overrides in custom
     * builders no longer compile if they keep it.
     */
    @Test
    void removesThrowsFromSecurityBuilderSubclassOverrides() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder;
              import org.springframework.security.config.annotation.ObjectPostProcessor;

              class GrpcSecurity extends AbstractConfiguredSecurityBuilder<String, GrpcSecurity> {
                  GrpcSecurity(ObjectPostProcessor<Object> objectPostProcessor) {
                      super(objectPostProcessor);
                  }

                  @Override
                  protected void beforeInit() throws Exception {
                  }

                  @Override
                  protected void beforeConfigure() throws Exception {
                  }

                  @Override
                  protected String performBuild() throws Exception {
                      return "built";
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.AbstractConfiguredSecurityBuilder;
              import org.springframework.security.config.annotation.ObjectPostProcessor;

              class GrpcSecurity extends AbstractConfiguredSecurityBuilder<String, GrpcSecurity> {
                  GrpcSecurity(ObjectPostProcessor<Object> objectPostProcessor) {
                      super(objectPostProcessor);
                  }

                  @Override
                  protected void beforeInit() {
                  }

                  @Override
                  protected void beforeConfigure() {
                  }

                  @Override
                  protected String performBuild() {
                      return "built";
                  }
              }
              """
          )
        );
    }

    @Test
    void removesThrowsFromAbstractSecurityBuilderDoBuild() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.AbstractSecurityBuilder;

              class CustomBuilder extends AbstractSecurityBuilder<String> {
                  @Override
                  protected String doBuild() throws Exception {
                      return "built";
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.AbstractSecurityBuilder;

              class CustomBuilder extends AbstractSecurityBuilder<String> {
                  @Override
                  protected String doBuild() {
                      return "built";
                  }
              }
              """
          )
        );
    }
}
