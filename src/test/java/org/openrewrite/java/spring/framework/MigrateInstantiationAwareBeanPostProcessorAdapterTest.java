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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateInstantiationAwareBeanPostProcessorAdapterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateInstantiationAwareBeanPostProcessorAdapter())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans"));
    }

    @DocumentExample
    @Test
    void migrateInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

              class A extends InstantiationAwareBeanPostProcessorAdapter {
              }
              """,
            """
              import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;

              class A implements SmartInstantiationAwareBeanPostProcessor {
              }
              """
          )
        );
    }

    @Test
    void changesTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

              public class A {
                  private final InstantiationAwareBeanPostProcessorAdapter processorAdapter;
              }
              """,
            """
              import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;

              public class A {
                  private final SmartInstantiationAwareBeanPostProcessor processorAdapter;
              }
              """
          )
        );
    }
}
