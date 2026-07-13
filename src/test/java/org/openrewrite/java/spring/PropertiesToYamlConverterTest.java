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
package org.openrewrite.java.spring;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.tree.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesToYamlConverterTest {

    private static String convert(@Language("properties") String properties) {
        Properties.File file = (Properties.File) PropertiesParser.builder().build()
          .parse(properties)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Failed to parse properties"));
        return PropertiesToYamlConverter.convert(file);
    }

    @Test
    void emptyFileYieldsEmptyString() {
        assertThat(convert("")).isEmpty();
    }

    @Test
    void multiplePropertiesBuildNestedMappings() {
        assertThat(convert(
          """
            server.port=8080
            spring.application.name=myapp
            """))
          .isEqualTo(
            """
              server:
                port: 8080
              spring:
                application:
                  name: myapp
              """);
    }

    @Test
    void valueContainingColonIsQuoted() {
        assertThat(convert("spring.datasource.url=jdbc:h2:mem:db"))
          .isEqualTo(
            """
              spring:
                datasource:
                  url: "jdbc:h2:mem:db"
              """);
    }

    @Test
    void valueContainingHashIsQuoted() {
        assertThat(convert("app.message=hello # world"))
          .isEqualTo(
            """
              app:
                message: "hello # world"
              """);
    }

    @Test
    void emptyValueMapsToEmptyString() {
        assertThat(convert("server.context-path="))
          .isEqualTo(
            """
              server:
                context-path: ""
              """);
    }

    @Test
    void yamlBooleanLikeValuesAreQuotedToPreserveStringSemantics() {
        assertThat(convert(
          """
            app.a=yes
            app.b=off
            app.c=on
            app.d=true
            """))
          .isEqualTo(
            """
              app:
                a: "yes"
                b: "off"
                c: "on"
                d: true
              """);
    }

    @Test
    void yamlNullLikeValuesAreQuoted() {
        assertThat(convert(
          """
            app.a=null
            app.b=~
            """))
          .isEqualTo(
            """
              app:
                a: "null"
                b: "~"
              """);
    }

    @Test
    void valuesReTypedByYamlAreQuotedOthersStayPlain() {
        assertThat(convert(
          """
            app.a=0x1A
            app.b=1_000
            app.c=+1
            app.d=2001-12-14
            app.e=1.50
            app.f=8080
            app.g=1.5
            """))
          .isEqualTo(
            """
              app:
                a: "0x1A"
                b: "1_000"
                c: "+1"
                d: "2001-12-14"
                e: "1.50"
                f: 8080
                g: 1.5
              """);
    }

    @Test
    void octalLikeValueIsQuoted() {
        assertThat(convert("app.file-mask=0755"))
          .isEqualTo(
            """
              app:
                file-mask: "0755"
              """);
    }

    @Test
    void escapedNewlineAndTabAreTranslated() {
        assertThat(convert("app.message=line1\\nline2\\tend"))
          .isEqualTo(
            """
              app:
                message: "line1\\nline2\\tend"
              """);
    }

    @Test
    void escapedBackslashAndUnicodeAreTranslated() {
        assertThat(convert("app.path=C:\\\\data\\u0021"))
          .isEqualTo(
            """
              app:
                path: "C:\\\\data!"
              """);
    }

    @Test
    void escapedKeysAreUnescaped() {
        assertThat(convert(
          """
            my\\ key=1
            a\\:b=2
            """))
          .isEqualTo(
            """
              my key: 1
              a:b: 2
              """);
    }

    @Test
    void keyThatIsAlsoAPrefixOfOtherKeysStaysLiteral() {
        assertThat(convert(
          """
            a=1
            a.b=2
            b.c.d=3
            b.c=4
            """))
          .isEqualTo(
            """
              a: 1
              a.b: 2
              b:
                c.d: 3
                c: 4
              """);
    }

    @Test
    void commentLinesArePreserved() {
        assertThat(convert(
          """
            # header comment
            server.port=8080
            ! also a comment
            spring.application.name=myapp
            # trailing comment
            """))
          .isEqualTo(
            """
              server:
                # header comment
                port: 8080
              spring:
                application:
                  # also a comment
                  name: myapp
              # trailing comment
              """);
    }

    @Test
    void indexedKeysAreConvertedToYamlSequence() {
        assertThat(convert(
          """
            my.list[0]=a
            other.key=x
            my.list[1]=b
            """))
          .isEqualTo(
            """
              my:
                list:
                  - a
                  - b
              other:
                key: x
              """);
    }

    @Test
    void outOfOrderIndexedKeysAreSortedIntoSequence() {
        assertThat(convert(
          """
            my.list[1]=b
            my.list[0]=a
            """))
          .isEqualTo(
            """
              my:
                list:
                  - a
                  - b
              """);
    }

    @Test
    void sequenceValuesAreQuotedWhenNeeded() {
        assertThat(convert(
          """
            app.urls[0]=jdbc:h2:mem:db
            app.urls[1]=plain
            """))
          .isEqualTo(
            """
              app:
                urls:
                  - "jdbc:h2:mem:db"
                  - plain
              """);
    }

    @Test
    void nonContiguousIndexedKeysRemainFlat() {
        assertThat(convert(
          """
            my.list[0]=a
            my.list[2]=c
            """))
          .isEqualTo(
            """
              my:
                list[0]: a
                list[2]: c
              """);
    }

    @Test
    void objectListKeysAreConvertedToYamlSequence() {
        assertThat(convert(
          """
            my.servers[0].host=alpha
            my.servers[0].port=8080
            my.servers[1].host=beta
            my.servers[1].port=9090
            """))
          .isEqualTo(
            """
              my:
                servers:
                  - host: alpha
                    port: 8080
                  - host: beta
                    port: 9090
              """);
    }

    @Test
    void nestedListInsideObjectListIsConverted() {
        assertThat(convert(
          """
            my.servers[0].host=alpha
            my.servers[0].tags[0]=x
            my.servers[0].tags[1]=y
            """))
          .isEqualTo(
            """
              my:
                servers:
                  - host: alpha
                    tags:
                      - x
                      - y
              """);
    }

    @Test
    void nonContiguousObjectListKeysRemainFlat() {
        assertThat(convert(
          """
            my.servers[0].host=alpha
            my.servers[2].host=gamma
            """))
          .isEqualTo(
            """
              my:
                servers[0]:
                  host: alpha
                servers[2]:
                  host: gamma
              """);
    }
}
