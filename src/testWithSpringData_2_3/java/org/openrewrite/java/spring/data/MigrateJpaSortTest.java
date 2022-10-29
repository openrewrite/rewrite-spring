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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJpaSortTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJpaSort())
          .parser(JavaParser.fromJavaVersion().classpath("javax.persistence", "spring-data-jpa", "spring-data-commons"));
    }

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
                  JpaSort onlyAttr = new JpaSort(attr);
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  JpaSort onlyAttr = JpaSort.of(attr);
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
                  JpaSort directionAndAttr = new JpaSort(Direction.DESC, attr);
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.domain.Sort.Direction;
              import javax.persistence.metamodel.Attribute;

              class Test {
                  Attribute<?, ?> attr;
                  JpaSort directionAndAttr = JpaSort.of(Direction.DESC, attr);
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
                  JpaSort onlyPath = new JpaSort(path);
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;

              class Test {
                  Path<?, ?> path;
                  JpaSort onlyPath = JpaSort.of(path);
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
                  JpaSort directionAndPath = new JpaSort(Direction.DESC, path);
              }
              """,
            """
              import org.springframework.data.jpa.domain.JpaSort;
              import org.springframework.data.jpa.domain.JpaSort.Path;
              import org.springframework.data.domain.Sort.Direction;

              class Test {
                  Path<?, ?> path;
                  JpaSort directionAndPath = JpaSort.of(Direction.DESC, path);
              }
              """
          )
        );
    }
}
