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

import static org.openrewrite.java.Assertions.java;

class MigrateBase64UtilsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateBase64Utils())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-core-5.+"));
    }

    @DocumentExample
    @Test
    void encodeDecode() {
        rewriteRun(
          //language=java
          java(
            """
              import java.nio.charset.StandardCharsets;
              import org.springframework.util.Base64Utils;

              class Test {
                  void test(byte[] bBytes) {
                      String key = "abc";
                      byte[] encoded1 = Base64Utils.encode(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded1 = Base64Utils.decode(key.getBytes(StandardCharsets.UTF_8));
                      byte[] encoded2 = Base64Utils.encodeUrlSafe(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded2 = Base64Utils.decodeUrlSafe(key.getBytes(StandardCharsets.UTF_8));
                      String encoded3 = Base64Utils.encodeToString(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded3 = Base64Utils.decodeFromString(key);
                      String encoded4 = Base64Utils.encodeToUrlSafeString(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded4 = Base64Utils.decodeFromUrlSafeString(key);
                  }
              }
              """,
            """
              import java.nio.charset.StandardCharsets;
              import java.util.Base64;

              class Test {
                  void test(byte[] bBytes) {
                      String key = "abc";
                      byte[] encoded1 = Base64.getEncoder().encode(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded1 = Base64.getDecoder().decode(key.getBytes(StandardCharsets.UTF_8));
                      byte[] encoded2 = Base64.getUrlEncoder().encode(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded2 = Base64.getUrlDecoder().decode(key.getBytes(StandardCharsets.UTF_8));
                      String encoded3 = Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded3 = Base64.getDecoder().decode(key);
                      String encoded4 = Base64.getUrlEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
                      byte[] decoded4 = Base64.getUrlDecoder().decode(key);
                  }
              }
              """
          )
        );
    }

}
