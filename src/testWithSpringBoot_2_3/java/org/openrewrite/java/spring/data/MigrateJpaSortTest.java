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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJpaSortTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJpaSort())
          .parser(JavaParser.fromJavaVersion().classpath("javax.persistence-api", "spring-data-jpa", "spring-data-commons"));
    }

    @DocumentExample
    @Test
    void constructorWithAttribute() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  void test() {
                      JpaSort onlyAttr = new JpaSort(attr);
                  }
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  void test() {
                      JpaSort onlyAttr = JpaSort.of(attr);
                  }
              }
              """
          )
        );
    }

    @Disabled("see an error: AST contains missing or invalid type information")
    @Test
    void constructorWithAttributeArray() {
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  void test(Attribute<?, ?>... attributes) {
                      JpaSort onlyAttr = new JpaSort(attributes);
                  }
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  void test(Attribute<?, ?>... attributes) {
                      JpaSort onlyAttr = JpaSort.of(attributes);
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorWithDirectionAndAttribute() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.domain.Sort.Direction;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  void test() {
                      JpaSort onlyAttr = new JpaSort(Direction.DESC, attr);
                  }
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.domain.Sort.Direction;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  void test() {
                      JpaSort onlyAttr = JpaSort.of(Direction.DESC, attr);
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorWithPath() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;

              class Test {
                  Path<?, ?> path;
                  void test() {
                      JpaSort onlyAttr = new JpaSort(path);
                  }
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;

              class Test {
                  Path<?, ?> path;
                  void test() {
                      JpaSort onlyAttr = JpaSort.of(path);
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorWithDirectionAndPath() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;
              import org.springframework.data.domain.Sort.Direction;

              class Test {
                  Path<?, ?> path;
                  void test() {
                      JpaSort onlyAttr = new JpaSort(Direction.DESC, path);
                  }
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;
              import org.springframework.data.domain.Sort.Direction;

              class Test {
                  Path<?, ?> path;
                  void test() {
                      JpaSort onlyAttr = JpaSort.of(Direction.DESC, path);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRewriteJpaPackageItself() {
        rewriteRun(
          java(
            """
              package org.springframework.data.jpa.domain;

              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;
 
              public class A {
                  public static JpaSort test(Attribute<?, ?>... attributes) {
                      return new JpaSort(attributes);
                  }
              }
              """
          )
        );
    }
}
