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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateResourceHttpMessageWriterAddHeadersMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .typeValidationOptions(TypeValidation.none())
          .recipe(new MigrateResourceHttpMessageWriterAddHeadersMethod())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web-6.1.+", "spring-core"));
    }

    @DocumentExample
    @Test
    void migrateResourceHttpMessageWriterAddHeadersMethod() {
        rewriteRun(
          // language=java
          java(
            """
              import org.springframework.web.reactive.HandlerResult;
              import org.springframework.http.ReactiveHttpOutputMessage;
              import org.springframework.core.io.Resource;
              import org.springframework.http.MediaType;
              import java.util.Map;
              import org.springframework.http.codec.ResourceHttpMessageWriter;

              class A {
                  void writeResourceWithHeaders(ReactiveHttpOutputMessage message, Resource resource, MediaType contentType, Map<String, Object> hints) {
                      ResourceHttpMessageWriter writer = new ResourceHttpMessageWriter();
                      writer.addHeaders(message, resource, contentType, hints);
                  }
              }
              """,
            """
              import org.springframework.web.reactive.HandlerResult;
              import org.springframework.http.ReactiveHttpOutputMessage;
              import org.springframework.core.io.Resource;
              import org.springframework.http.MediaType;
              import java.util.Map;
              import org.springframework.http.codec.ResourceHttpMessageWriter;

              class A {
                  void writeResourceWithHeaders(ReactiveHttpOutputMessage message, Resource resource, MediaType contentType, Map<String, Object> hints) {
                      ResourceHttpMessageWriter writer = new ResourceHttpMessageWriter();
                      writer.addDefaultHeaders(message, resource, contentType, hints).block();
                  }
              }
              """
          )
        );
    }
}
