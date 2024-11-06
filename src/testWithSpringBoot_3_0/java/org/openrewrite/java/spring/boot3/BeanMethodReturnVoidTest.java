/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import static org.openrewrite.java.Assertions.java;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class BeanMethodReturnVoidTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BeanMethodReturnNull())
          .parser(JavaParser.fromJavaVersion().classpath("spring-context"));
    }

    @DocumentExample
    @Test
    void transformReturnType() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class Test {

                  @Bean
                  public void myBean() {
                  }
              }
              """, """
              import org.springframework.context.annotation.Bean;

              public class Test {

                  @Bean
                  public Object myBean() {
                      return null;
                  }
              }
              """)
        );
    }

    @Test
    void transformAllReturn() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class Test {

                  @Bean
                  public void bar() {
                      if (true) {
                          return;
                      }
                      int i = 1;
                  }

                  @Bean
                  public Object foo() {
                      return "foo";
                  }
              }
              """, """
              import org.springframework.context.annotation.Bean;

              public class Test {

                  @Bean
                  public Object bar() {
                      if (true) {
                          return null;
                      }
                      int i = 1;
                      return null;
                  }

                  @Bean
                  public Object foo() {
                      return "foo";
                  }
              }
              """)
        );
    }
}
