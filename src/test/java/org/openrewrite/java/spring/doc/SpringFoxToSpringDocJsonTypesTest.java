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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringFoxToSpringDocJsonTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.springdoc.SpringFoxToSpringDoc")
          .parser(JavaParser.fromJavaVersion()
            //language=java
            .dependsOn(
              """
                package springfox.documentation.spring.web.json;
                public class Json {
                    private final String value;
                    public Json(String value) { this.value = value; }
                    public String value() { return value; }
                }
                """,
              """
                package springfox.documentation.spring.web.json;
                import java.util.List;
                public class JsonSerializer {
                    public JsonSerializer(List<JacksonModuleRegistrar> modules) {}
                    public Json toJson(Object toSerialize) { return new Json(""); }
                }
                """,
              """
                package springfox.documentation.spring.web.json;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public interface JacksonModuleRegistrar {
                    void maybeRegisterModule(ObjectMapper objectMapper);
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void addTodoCommentToJsonSerializerConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.spring.web.json.JacksonModuleRegistrar;
              import springfox.documentation.spring.web.json.JsonSerializer;
              import java.util.List;

              class SwaggerConfig {

                  public JsonSerializer jsonSerializer(List<JacksonModuleRegistrar> registrars) {
                      return new JsonSerializer(registrars);
                  }
              }
              """,
            """
              import springfox.documentation.spring.web.json.JacksonModuleRegistrar;
              import springfox.documentation.spring.web.json.JsonSerializer;
              import java.util.List;

              class SwaggerConfig {

                  public JsonSerializer jsonSerializer(List<JacksonModuleRegistrar> registrars) {
                      return /* TODO: springfox.documentation.spring.web.json types have no SpringDoc equivalent. Remove this Springfox-internal code and use Jackson ObjectMapper directly if JSON serialization is still needed. See https://springdoc.org/migrating-from-springfox.html for guidance. */ new JsonSerializer(registrars);
                  }
              }
              """
          )
        );
    }

    @Test
    void addTodoCommentToJsonConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.spring.web.json.Json;

              class SwaggerConfig {

                  public Json createJson(String value) {
                      return new Json(value);
                  }
              }
              """,
            """
              import springfox.documentation.spring.web.json.Json;

              class SwaggerConfig {

                  public Json createJson(String value) {
                      return /* TODO: springfox.documentation.spring.web.json types have no SpringDoc equivalent. Remove this Springfox-internal code and use Jackson ObjectMapper directly if JSON serialization is still needed. See https://springdoc.org/migrating-from-springfox.html for guidance. */ new Json(value);
                  }
              }
              """
          )
        );
    }
}
