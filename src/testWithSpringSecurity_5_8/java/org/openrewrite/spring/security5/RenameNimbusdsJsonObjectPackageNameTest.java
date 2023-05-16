/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RenameNimbusdsJsonObjectPackageNameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.security5.RenameNimbusdsJsonObjectPackageName"))
          .parser(JavaParser.fromJavaVersion().classpath("nimbus-jose-jwt"));
    }

    @Test
    void renamePackage() {
        rewriteRun(
          java(
            """
              import com.nimbusds.jose.shaded.json.JSONObject;
              import com.nimbusds.jose.shaded.json.JSONValue;

              public class TutorialService {
                JSONObject dsl;
                JSONValue value;

                public JSONObject getDsl() {
                  return dsl;
                }

                public JSONValue getValue() {
                  return value;
                }
              }
              """
            ,
            """
              import net.minidev.json.JSONObject;
              import net.minidev.json.JSONValue;

              public class TutorialService {
                JSONObject dsl;
                JSONValue value;

                public JSONObject getDsl() {
                  return dsl;
                }

                public JSONValue getValue() {
                  return value;
                }
              }
              """
          )
        );
    }
}
