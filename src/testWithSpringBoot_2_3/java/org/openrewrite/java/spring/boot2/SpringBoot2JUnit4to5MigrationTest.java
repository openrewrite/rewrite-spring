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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringBoot2JUnit4to5MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes(
              "org.openrewrite.java.spring.boot2.UnnecessarySpringRunWith",
              "org.openrewrite.java.spring.boot2.UnnecessarySpringExtension",
              "org.openrewrite.java.testing.junit5.JUnit4to5Migration"
            )
          )
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot-test", "junit", "spring-test"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/43")
    @Test
    void springBootRunWithRemovedNoExtension() {
        //language=java
        rewriteRun(
          java(
            """
              package org.springframework.samples.petclinic.system;
                            
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.test.context.junit4.SpringRunner;
                            
              @SpringBootTest
              @RunWith(SpringRunner.class)
              public class ProductionConfigurationTests {
                            
                  @Test
                  public void testFindAll() {
                  }
              }
              """,
            """
              package org.springframework.samples.petclinic.system;
                            
              import org.junit.jupiter.api.Test;
              import org.springframework.boot.test.context.SpringBootTest;
                            
              @SpringBootTest
              public class ProductionConfigurationTests {
                            
                  @Test
                  void testFindAll() {
                  }
              }
              """
          )
        );
    }
}
