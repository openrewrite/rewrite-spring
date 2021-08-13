/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class OutputCaptureExtensionTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot-test", "hamcrest-core", "junit")
            .logCompilationWarningsAndErrors(true)
            .build()

    override val recipe: Recipe
        get() = OutputCaptureExtension()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/46")
    @Test
    fun outputCaptureExtension() = assertChanged(
        before = """
            import org.junit.Rule;
            import org.springframework.boot.test.rule.OutputCapture;
            
            class Test {
                @Rule
                OutputCapture capture = new OutputCapture();
            
                void test() {
                    System.out.println(capture.toString());
                    System.out.println(this.capture.toString());
                }
            
                void doesntUse() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.springframework.boot.test.system.CapturedOutput;
            import org.springframework.boot.test.system.OutputCaptureExtension;

            @ExtendWith(OutputCaptureExtension.class)
            class Test {
            
                void test(CapturedOutput capture) {
                    System.out.println(capture.toString());
                    System.out.println(capture.toString());
                }
            
                void doesntUse() {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/51")
    @Test
    fun outputCaptureMatcherConversion() = assertChanged(
        before = """
            import org.hamcrest.CoreMatchers;
            import org.junit.Rule;
            import org.springframework.boot.test.rule.OutputCapture;
            
            class Test {
                @Rule
                OutputCapture capture = new OutputCapture();
            
                void test() {
                    System.out.println("I am here");
                    this.capture.expect(CoreMatchers.containsString("here"));
                }
            
                void doesntUse() {
                }
            }
        """,
        after = """
            import org.hamcrest.CoreMatchers;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.springframework.boot.test.system.CapturedOutput;
            import org.springframework.boot.test.system.OutputCaptureExtension;
            
            @ExtendWith(OutputCaptureExtension.class)
            class Test {
            
                void test(CapturedOutput capture) {
                    System.out.println("I am here");
                    CoreMatchers.containsString("here").matches(capture.getAll());
                }
            
                void doesntUse() {
                }
            }
        """,
        typeValidation = { identifiers = false; methodInvocations = false }
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/50")
    @Test
    fun onlyCaptureOutputCaptureRules() = assertUnchanged(
        before = """
            class FooConfig {
                void test(String name) {
                    System.out.println(name);
                }
            }
        """
    )
}
