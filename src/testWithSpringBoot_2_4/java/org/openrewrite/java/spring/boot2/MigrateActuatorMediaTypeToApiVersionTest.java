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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateActuatorMediaTypeToApiVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateActuatorMediaTypeToApiVersion())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot-actuator", "spring-web", "spring-core"));
    }

    @Test
    void fromConstantToEnumVal() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.actuate.endpoint.http.ActuatorMediaType;
              import org.springframework.http.MediaType;
              
              class T {
                  private static final MediaType actuatorMediaType2 = MediaType.parseMediaType(ActuatorMediaType.V2_JSON);
                  private static final MediaType actuatorMediaType3 = MediaType.parseMediaType(ActuatorMediaType.V3_JSON);
              }
              """,
            """
              import org.springframework.boot.actuate.endpoint.ApiVersion;
              import org.springframework.http.MediaType;
              
              class T {
                  private static final MediaType actuatorMediaType2 = MediaType.asMediaType(ApiVersion.V2.getProducedMimeType());
                  private static final MediaType actuatorMediaType3 = MediaType.asMediaType(ApiVersion.V3.getProducedMimeType());
              }
              """
          )
        );
    }
}
