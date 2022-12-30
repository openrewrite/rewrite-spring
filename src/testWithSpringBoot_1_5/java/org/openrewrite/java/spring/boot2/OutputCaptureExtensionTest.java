/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("MethodMayBeStatic")
class OutputCaptureExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OutputCaptureExtension())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot-test", "hamcrest", "junit"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/46")
    @Test
    void outputCaptureExtension() {
        //language=java
        rewriteRun(
          java(
            """
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
            """
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
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/51")
    @Test
    void outputCaptureMatcherConversion() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
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
            """
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
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/50")
    @Test
    void onlyCaptureOutputCaptureRules() {
        //language=java
        rewriteRun(
          java(
            """
              class FooConfig {
                  void test(String name) {
                      System.out.println(name);
                  }
              }
              """
          )
        );
    }
}
