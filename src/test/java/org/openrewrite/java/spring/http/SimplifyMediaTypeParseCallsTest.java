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
package org.openrewrite.java.spring.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyMediaTypeParseCallsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new SimplifyMediaTypeParseCalls())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6.+", "spring-core-6.+"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "ALL_VALUE",
      "APPLICATION_ATOM_XML_VALUE",
      "APPLICATION_CBOR_VALUE",
      "APPLICATION_FORM_URLENCODED_VALUE",
      "APPLICATION_JSON_VALUE",
      "APPLICATION_JSON_UTF8_VALUE",
      "APPLICATION_OCTET_STREAM_VALUE",
      "APPLICATION_PDF_VALUE",
      "APPLICATION_PROBLEM_JSON_VALUE",
      "APPLICATION_PROBLEM_JSON_UTF8_VALUE",
      "APPLICATION_PROBLEM_XML_VALUE",
      "APPLICATION_RSS_XML_VALUE",
      "APPLICATION_STREAM_JSON_VALUE",
      "APPLICATION_XHTML_XML_VALUE",
      "APPLICATION_XML_VALUE",
      "IMAGE_GIF_VALUE",
      "IMAGE_JPEG_VALUE",
      "IMAGE_PNG_VALUE",
      "MULTIPART_FORM_DATA_VALUE",
      "MULTIPART_MIXED_VALUE",
      "MULTIPART_RELATED_VALUE",
      "TEXT_EVENT_STREAM_VALUE",
      "TEXT_HTML_VALUE",
      "TEXT_MARKDOWN_VALUE",
      "TEXT_PLAIN_VALUE",
      "TEXT_XML_VALUE"
    })
    void replaceMediaTypes(String value) {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.MediaType;
              
              import static org.springframework.http.MediaType.parseMediaType;
              import static org.springframework.http.MediaType.valueOf;
              
              class Test {
                  MediaType mediaType1 = MediaType.parseMediaType(MediaType.%1$s);
                  MediaType mediaType2 = MediaType.valueOf(MediaType.%1$s);
                  MediaType mediaType3 = parseMediaType(MediaType.%1$s);
                  MediaType mediaType4 = valueOf(MediaType.%1$s);
              }
              """.formatted(value),
            """
              import org.springframework.http.MediaType;
              
              class Test {
                  MediaType mediaType1 = MediaType.%1$s;
                  MediaType mediaType2 = MediaType.%1$s;
                  MediaType mediaType3 = MediaType.%1$s;
                  MediaType mediaType4 = MediaType.%1$s;
              }
              """.formatted(value.replace("_VALUE", ""))
          )
        );
    }
}
