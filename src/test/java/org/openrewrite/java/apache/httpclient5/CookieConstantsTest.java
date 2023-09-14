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
package org.openrewrite.java.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class CookieConstantsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("httpclient", "httpcore", "httpclient5", "httpcore5"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5")
          );
    }

    @Test
    void testCookieConstantsMapping() {
        rewriteRun(
          //language=java
          java(
            """
                import org.apache.http.client.config.CookieSpecs;
                
                class A {
                    void method() {
                        String c1 = CookieSpecs.IGNORE_COOKIES;
                        String c2 = CookieSpecs.STANDARD;
                        String c3 = CookieSpecs.STANDARD_STRICT;
                    }
                }
            """, """
                import org.apache.hc.client5.http.cookie.StandardCookieSpec;
                
                class A {
                    void method() {
                        String c1 = StandardCookieSpec.IGNORE;
                        String c2 = StandardCookieSpec.RELAXED;
                        String c3 = StandardCookieSpec.STRICT;
                    }
                }
            """
          )
        );
    }
}
