/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateLocalServerPortAnnotationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLocalServerPortAnnotation())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/116")
    @Test
    void givenHasStringVariableWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.embedded.LocalServerPort;

              public class RandomTestClass {
                  @LocalServerPort
                  private int port;
              }
              """,
            """
              import org.springframework.boot.web.server.LocalServerPort;
              
              public class RandomTestClass {
                  @LocalServerPort
                  private int port;
              }
              """
          )
        );
    }
}
