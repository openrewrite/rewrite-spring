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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.UpdateSCryptPasswordEncoder;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpdateSCryptPasswordEncoderTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateSCryptPasswordEncoder())
          .parser(JavaParser.fromJavaVersion().classpath("spring-security-crypto-5.+"));
    }

    @DocumentExample
    @Test
    void replaceDefaultConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = new SCryptPasswordEncoder();
                  }
              }
              """,
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceFullConstructorCallMatchingV41Defaults() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  final int saltLength = 64;
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, 64);
                  }
                  void encoderWithDefaultsUsingSomeConstant() {
                      SCryptPasswordEncoder encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, saltLength);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  final int saltLength = 64;
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1();
                  }
                  void encoderWithDefaultsUsingSomeConstant() {
                      SCryptPasswordEncoder encoder = SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceFullConstructorCallMatchingV58Defaults() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = new SCryptPasswordEncoder(65536, 8, 1, 32, 16);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      SCryptPasswordEncoder encoder = SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveFullConstructorCallUsingNonFinalConstant() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

              class T {
                  int saltLength = 64;
                  void encoderWithDefaultsNotAsConstant() {
                      SCryptPasswordEncoder encoder = new SCryptPasswordEncoder(16384, 8, 1, 32, saltLength);
                  }
              }
              """
          )
        );
    }
}
