/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.test;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceJUnit4SpringTestBaseClassesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceJUnit4SpringTestBaseClasses())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-test-5.3.+", "spring-tx-4.1.+", "spring-context-5.+", "junit-4", "junit-jupiter-api"));
    }

    @DocumentExample
    @Test
    void replaceAbstractJUnit4SpringContextTests() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

              public class MyTest extends AbstractJUnit4SpringContextTests {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceAbstractTransactionalJUnit4SpringContextTests() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;

              public class MyTransactionalTest extends AbstractTransactionalJUnit4SpringContextTests {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
              import org.springframework.transaction.annotation.Transactional;

              @ExtendWith(SpringExtension.class)
              @Transactional
              public class MyTransactionalTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotAddDuplicateExtendWith() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
              import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

              @ExtendWith(SpringExtension.class)
              public class MyTest extends AbstractJUnit4SpringContextTests {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;

              @ExtendWith(SpringExtension.class)
              public class MyTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotAddDuplicateTransactional() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
              import org.springframework.transaction.annotation.Transactional;

              @Transactional
              public class MyTransactionalTest extends AbstractTransactionalJUnit4SpringContextTests {

                  @Test
                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
              import org.springframework.transaction.annotation.Transactional;

              @ExtendWith(SpringExtension.class)
              @Transactional
              public class MyTransactionalTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotExtendingBaseClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;

              public class RegularTest {

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }
}
