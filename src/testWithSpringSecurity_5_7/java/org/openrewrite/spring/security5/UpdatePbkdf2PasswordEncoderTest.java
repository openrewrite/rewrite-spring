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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.UpdatePbkdf2PasswordEncoder;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpdatePbkdf2PasswordEncoderTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdatePbkdf2PasswordEncoder())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-security-crypto-5.8.+"));
    }

    @DocumentExample
    @Test
    void replaceDefaultConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder();
                  }
              }
              """,
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOneArgConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("");
                  }
                  void encoderWithCustomSecret() {
                      String secret = "secret";
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(secret);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
                  void encoderWithCustomSecret() {
                      String secret = "secret";
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(secret, 8, 185000, PBKDF2WithHmacSHA256);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceTwoArgConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  final int finalSaltLength = 8;
                  int nonFinalSaltLength = 8;
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 8);
                  }
                  void encoderWithDefaultsFromConstant() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", finalSaltLength);
                  }
                  void encoderWithCustomSecretAndSaltLength() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 20);
                  }
                  void encoderWithVariableSaltLength() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", nonFinalSaltLength);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

              class T {
                  final int finalSaltLength = 8;
                  int nonFinalSaltLength = 8;
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
                  void encoderWithDefaultsFromConstant() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
                  void encoderWithCustomSecretAndSaltLength() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 20, 185000, PBKDF2WithHmacSHA256);
                  }
                  void encoderWithVariableSaltLength() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", nonFinalSaltLength, 185000, PBKDF2WithHmacSHA256);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceThreeArgConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 185000, 256);
                  }
                  void encoderWithCustomSecretAndIterations() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 200000, 256);
                  }
                  void encoderWithCustomSecretIterationsAndHashHashWidth() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 200000, 320);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

              class T {
                  void encoderWithDefaults() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
                  void encoderWithCustomSecretAndIterations() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 8, 200000, PBKDF2WithHmacSHA256);
                  }
                  void encoderWithCustomSecretIterationsAndHashHashWidth() {
                      Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("secret", 8, 200000, 320);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceVersion55FactoryCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void m() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_5();
                  }
              }
              """,
            """
              import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

              class T {
                  void m() {
                      Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                  }
              }
              """
          )
        );
    }
}
