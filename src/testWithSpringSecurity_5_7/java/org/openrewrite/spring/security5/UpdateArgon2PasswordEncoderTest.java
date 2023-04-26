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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.UpdateArgon2PasswordEncoder;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpdateArgon2PasswordEncoderTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateArgon2PasswordEncoder())
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpathFromResources(new InMemoryExecutionContext(),"spring-security-crypto-5.8.+"));
    }

    @DocumentExample
    @Test
    void replaceDefaultConstructorCall() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = new Argon2PasswordEncoder();
                  }
              }
              """,
            """
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceFullConstructorCallMatchingV52Defaults() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  final int saltLength = 16;
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
                  }
                  void encoderWithDefaultsUsingSomeConstant() {
                      Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(saltLength, 32, 1, 4096, 3);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  final int saltLength = 16;
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2();
                  }
                  void encoderWithDefaultsUsingSomeConstant() {
                      Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2();
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
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 16384, 2);
                  }
              }
              """,
            """
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  void encoderWithDefaults() {
                      Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
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
              import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
              
              class T {
                  int saltLength = 64;
                  void encoderWithDefaultsNotAsConstant() {
                      Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16384, 8, 1, 32, saltLength);
                  }
              }
              """
          )
        );
    }
}
