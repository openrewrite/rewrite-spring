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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.spring.PreserveParameterWhitespace;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringBoot2BestPracticesWhitespaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreserveParameterWhitespace());
    }

    @DocumentExample
    @Test
    void preserveWhitespaceInMethodInvocation() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        method(1,  2, 3,   4);
                    }

                    void method(int a, int b, int c, int d) {
                    }
                }
                """
            )
        );
    }

    @Test
    void preserveWhitespaceInMethodDeclaration() {
        rewriteRun(
            java(
                """
                class Test {
                    void test(int a,  int b, int c) {
                    }
                }
                """
            )
        );
    }
}
