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
import org.openrewrite.Issue;
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
              import org.springframework.web.servlet.ModelAndView;

              class MyInterceptor extends HandlerInterceptorAdapter {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }

                  @Override
                  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                      super.postHandle(request, response, handler, modelAndView);
                  }

                  @Override
                  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
                      super.afterCompletion(request, response, handler, ex);
                  }
              }
              """,
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.HandlerInterceptor;
              import org.springframework.web.servlet.ModelAndView;

              class MyInterceptor implements HandlerInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return true;
                  }

                  @Override
                  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
                  }

                  @Override
                  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/620")
    @Test
    void doesNotReplaceInterceptorsExtendingOwnInterceptors() {
        //language=java
        rewriteRun(
          // Do change classes that directly extend HandlerInterceptorAdapter
          java(
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

              class MySuperInterceptor extends HandlerInterceptorAdapter {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }
              """,
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.HandlerInterceptor;

              class MySuperInterceptor implements HandlerInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return true;
                  }
              }
              """
          ),
          // But do not change classes that transitively extend HandlerInterceptorAdapter
          java(
            """
              import javax.servlet.http.*;

              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter; // Unused but untouched

              class MyInterceptor extends MySuperInterceptor {
                  @Override
                  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                      return super.preHandle(request, response, handler);
                  }
              }
              """
          ),
          // And do not change any other super call when HandlerInterceptorAdapter is used
          java(
            """
              import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

              class B extends A {
                  @Override
                  public String test() {
                      new HandlerInterceptorAdapter() {};
                      return super.test();
                  }
              }

              class A {
                  public String test() {
                      return "test";
                  }
              }
              """
          )
        );
    }
}
