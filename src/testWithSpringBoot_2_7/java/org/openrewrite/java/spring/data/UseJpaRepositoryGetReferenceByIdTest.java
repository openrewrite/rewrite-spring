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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseJpaRepositoryGetReferenceByIdTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-data-commons-2.7", "spring-data-jpa-2.7"))
          .recipeFromResource("/META-INF/rewrite/spring-data-27.yml", "org.openrewrite.java.spring.data.UseJpaRepositoryGetReferenceById");
    }

    @Test
    @DocumentExample
    void matchAndUpdateReferences() {
        //language=java
        rewriteRun(
          java(
            """
              package foo;
              public class Book {}
              """
          ),
          java(
            """
              package foo;
              import org.springframework.data.jpa.repository.JpaRepository;
              public interface BookRepository extends JpaRepository<Book, Long> {
              }
              """
          ),
          java(
            """
              import foo.*;
              class A {
                  BookRepository repo;
                  void method(Long id) {
                      repo.getById(id);
                      repo.getOne(id);
                  }
              }
              """,
            """
              import foo.*;
              class A {
                  BookRepository repo;
                  void method(Long id) {
                      repo.getReferenceById(id);
                      repo.getReferenceById(id);
                  }
              }
              """
          )
        );
    }
}
