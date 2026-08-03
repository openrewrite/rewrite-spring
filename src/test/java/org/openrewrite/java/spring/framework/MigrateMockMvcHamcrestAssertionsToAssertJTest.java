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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateMockMvcHamcrestAssertionsToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMockMvcHamcrestAssertionsToAssertJ())
          .parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package org.springframework.test.web.servlet;
              public class MockMvc {
                  public ResultActions perform(RequestBuilder requestBuilder) {
                      return null;
                  }
              }
              """,
            """
              package org.springframework.test.web.servlet;
              public interface RequestBuilder {
              }
              """,
            """
              package org.springframework.test.web.servlet;
              public interface ResultMatcher {
              }
              """,
            """
              package org.springframework.test.web.servlet;
              public interface ResultActions {
                  ResultActions andExpect(ResultMatcher matcher);
                  ResultActions andExpectAll(ResultMatcher... matchers);
              }
              """,
            """
              package org.springframework.test.web.servlet.request;
              import org.springframework.test.web.servlet.RequestBuilder;
              public final class MockMvcRequestBuilders {
                  public static RequestBuilder get(String uri, Object... uriVariables) {
                      return null;
                  }
              }
              """,
            """
              package org.springframework.test.web.servlet.result;
              import org.springframework.test.web.servlet.ResultMatcher;
              public final class MockMvcResultMatchers {
                  public static StatusResultMatchers status() {
                      return null;
                  }
                  public static final class StatusResultMatchers {
                      public ResultMatcher isOk() {
                          return null;
                      }
                  }
              }
              """,
            """
              package org.springframework.test.web.servlet.assertj;
              import org.springframework.test.web.servlet.MockMvc;
              import org.springframework.test.web.servlet.RequestBuilder;
              public final class MockMvcTester {
                  public static MockMvcTester create(MockMvc mockMvc) {
                      return null;
                  }
                  public MvcTestResult perform(RequestBuilder requestBuilder) {
                      return null;
                  }
              }
              """,
            """
              package org.springframework.test.web.servlet.assertj;
              import org.springframework.test.web.servlet.ResultMatcher;
              public class MvcTestResult {
              }
              """,
            """
              package org.springframework.test.web.servlet.assertj;
              import org.springframework.test.web.servlet.ResultMatcher;
              public class MvcTestResultAssert {
                  public MvcTestResultAssert matches(ResultMatcher matcher) {
                      return this;
                  }
              }
              """,
            """
              package org.assertj.core.api;
              import org.springframework.test.web.servlet.assertj.MvcTestResult;
              import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;
              public final class Assertions {
                  public static MvcTestResultAssert assertThat(MvcTestResult result) {
                      return null;
                  }
              }
              """
          ));
    }

    @DocumentExample
    @Test
    void migratesChainedAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.servlet.MockMvc;

              import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
              import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

              class Example {
                  void test(MockMvc mockMvc) {
                      mockMvc.perform(get("/accounts/{id}", 1))
                          .andExpect(status().isOk())
                          .andExpect(status().isOk());
                  }
              }
              """,
            """
              import org.springframework.test.web.servlet.MockMvc;
              import org.springframework.test.web.servlet.assertj.MockMvcTester;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
              import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

              class Example {
                  void test(MockMvc mockMvc) {
                      assertThat(MockMvcTester.create(mockMvc).perform(get("/accounts/{id}", 1)))
                          .matches(status().isOk())
                          .matches(status().isOk());
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotMigrateAndExpectAll() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.web.servlet.MockMvc;

              import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
              import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

              class Example {
                  void test(MockMvc mockMvc) {
                      mockMvc.perform(get("/accounts/{id}", 1))
                          .andExpectAll(status().isOk(), status().isOk());
                  }
              }
              """
          )
        );
    }
}
