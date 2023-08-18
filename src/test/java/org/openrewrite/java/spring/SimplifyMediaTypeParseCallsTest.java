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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.http.SimplifyMediaTypeParseCalls;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class SimplifyMediaTypeParseCallsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SimplifyMediaTypeParseCalls())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.+", "spring-core-6.+"));
    }

    @DocumentExample
    @Test
    void replaceUnnecessaryMediaTypeParseMediaTypeCall() {
        //language=java
        rewriteRun(
          java(
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  MediaType mediaType = MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE);
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  MediaType mediaType = MediaType.APPLICATION_JSON;
              }
              """
          )
        );
    }

    @Test
    void replaceUnnecessaryMediaTypeValueOfCall() {
        //language=java
        rewriteRun(
          java(
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE);
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.http.MediaType;
              
              class Test {
                  MediaType mediaType = MediaType.APPLICATION_JSON;
              }
              """
          )
        );
    }
}
