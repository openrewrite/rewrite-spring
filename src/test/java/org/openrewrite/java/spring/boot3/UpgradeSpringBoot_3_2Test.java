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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringBoot_3_2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2");
    }

    @Test
    void addCommonsCodecWhenUsed() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>fooservice</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .contains("<artifactId>commons-codec</artifactId>")
                .containsPattern("<version>1\\.(1[7-9]|[2-9]\\d)\\.\\d+</version>")
                .actual())
            ),
            srcMainJava(
              //language=java
              java(
                """
                  package org.apache.commons.codec.binary;

                  public class Hex {
                      public static String encodeHexString(byte[] data) {
                          return "";
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import org.apache.commons.codec.binary.Hex;

                  class A {
                      String hex(byte[] data) {
                          return Hex.encodeHexString(data);
                      }
                  }
                  """
              )
            )
          )
        );
    }
}
