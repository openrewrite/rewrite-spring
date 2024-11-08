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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.framework.MigrateHandlerInterceptor;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateHandlerInterceptorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("spring-webmvc", "javax.servlet-api"))
          .recipe(new MigrateHandlerInterceptor());
    }

    @DocumentExample
    @Test
    void migrateHandlerInterceptorAdapter() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

              class MyInterceptor extends HandlerInterceptorAdapter {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }
              """,
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.HandlerInterceptor;

              class MyInterceptor implements HandlerInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return HandlerInterceptor.super.preHandle(request, response, handler);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotReplaceInterceptorsExtendingOwnInterceptors() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

              class MyInterceptor extends MySuperInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }

              class MySuperInterceptor extends HandlerInterceptorAdapter {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }""",
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.HandlerInterceptor;

              class MyInterceptor extends MySuperInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }

              class MySuperInterceptor implements HandlerInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return HandlerInterceptor.super.preHandle(request, response, handler);
                  }
              }
              """
          )
        );
    }

    @Test
    void unusedImportOfHandlerInterceptorAdapterAndHasASuperCallShouldDoNothing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

              class MyInterceptorLike extends MySuperInterceptor {
                  @Override
                  public boolean test() {
                      return super.test();
                  }
                  @Override
                  public boolean test2() {
                      return super.test();
                  }
              }

              class MySuperInterceptor {
                  public boolean test() {
                      return true;
                  }
                  public boolean test2() {
                      return true;
                  }
              }"""
          )
        );
    }
}
