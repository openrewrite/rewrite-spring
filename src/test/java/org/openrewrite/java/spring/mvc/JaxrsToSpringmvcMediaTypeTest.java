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
package org.openrewrite.java.spring.mvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JaxrsToSpringmvcMediaTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JaxrsToSpringmvcMediaType())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jakarta.ws.rs-api-4", "javax.ws.rs-api-2"));
    }

    @DocumentExample
    @Test
    void migrateMediaTypeJavaxTest() {
        rewriteRun(
          java(
              """
              import javax.ws.rs.core.MediaType;
              import javax.ws.rs.Produces;

              public class TestExample {

                  @Produces(MediaType.APPLICATION_JSON)
                  public void updateUser() {}

                  public String getJsonType() {
                      return MediaType.APPLICATION_JSON;
                  }

                  public MediaType getFormType() {
                      return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
                  }
              }
              """,
              """
              import org.springframework.http.MediaType;

              import javax.ws.rs.Produces;

              public class TestExample {

                  @Produces(MediaType.APPLICATION_JSON_VALUE)
                  public void updateUser() {}

                  public String getJsonType() {
                      return MediaType.APPLICATION_JSON_VALUE;
                  }

                  public MediaType getFormType() {
                      return MediaType.APPLICATION_FORM_URLENCODED;
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateMediaTypeJakartaTest() {
        rewriteRun(
          java(
              """
              import jakarta.ws.rs.core.MediaType;
              import jakarta.ws.rs.Produces;

              public class TestExample {

                  @Produces(MediaType.APPLICATION_JSON)
                  public void updateUser() {}

                  public String getJsonType() {
                      return MediaType.APPLICATION_JSON;
                  }

                  public MediaType getFormType() {
                      return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
                  }
              }
              """,
              """
              import jakarta.ws.rs.Produces;
              import org.springframework.http.MediaType;

              public class TestExample {

                  @Produces(MediaType.APPLICATION_JSON_VALUE)
                  public void updateUser() {}

                  public String getJsonType() {
                      return MediaType.APPLICATION_JSON_VALUE;
                  }

                  public MediaType getFormType() {
                      return MediaType.APPLICATION_FORM_URLENCODED;
                  }
              }
              """
          )
        );
    }
}
