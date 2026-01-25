/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateFilterToOncePerRequestFilterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web-5", "javax.servlet-api-4"))
          .recipe(new MigrateFilterToOncePerRequestFilter());
    }

    @DocumentExample
    @Test
    void migrateBasicFilter() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.*;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter implements Filter {
                  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      // custom logic
                      chain.doFilter(request, response);
                  }
              }
              """,
            """
              import org.springframework.web.filter.OncePerRequestFilter;

              import javax.servlet.FilterChain;
              import javax.servlet.ServletException;
              import javax.servlet.ServletRequest;
              import javax.servlet.ServletResponse;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter extends OncePerRequestFilter {
                  @Override
                  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      // custom logic
                      chain.doFilter(request, response);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateFilterWithEmptyInitAndDestroy() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.*;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter implements Filter {
                  @Override
                  public void init(FilterConfig filterConfig) {
                  }

                  @Override
                  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }

                  @Override
                  public void destroy() {
                  }
              }
              """,
            """
              import org.springframework.web.filter.OncePerRequestFilter;

              import javax.servlet.FilterChain;
              import javax.servlet.ServletException;
              import javax.servlet.ServletRequest;
              import javax.servlet.ServletResponse;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter extends OncePerRequestFilter {

                  @Override
                  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateFilterPreservingNonEmptyInit() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.*;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter implements Filter {
                  private String configValue;

                  @Override
                  public void init(FilterConfig filterConfig) {
                      configValue = filterConfig.getInitParameter("myParam");
                  }

                  @Override
                  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }

                  @Override
                  public void destroy() {
                  }
              }
              """,
            """
              import org.springframework.web.filter.OncePerRequestFilter;

              import javax.servlet.*;
              import javax.servlet.http.*;
              import java.io.IOException;

              public class CustomFilter extends OncePerRequestFilter {
                  private String configValue;

                  @Override
                  public void init(FilterConfig filterConfig) {
                      configValue = filterConfig.getInitParameter("myParam");
                  }

                  @Override
                  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyClassAlreadyExtendingOncePerRequestFilter() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.web.filter.OncePerRequestFilter;
              import javax.servlet.FilterChain;
              import javax.servlet.ServletException;
              import javax.servlet.http.HttpServletRequest;
              import javax.servlet.http.HttpServletResponse;
              import java.io.IOException;

              public class CustomFilter extends OncePerRequestFilter {
                  @Override
                  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyClassNotImplementingFilter() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.http.*;
              import java.io.IOException;

              public class NotAFilter {
                  public void doSomething(HttpServletRequest request, HttpServletResponse response)
                          throws IOException {
                      // not a filter
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateFilterWithMultipleInterfaces() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.servlet.*;
              import javax.servlet.http.*;
              import java.io.IOException;
              import java.io.Serializable;

              public class CustomFilter implements Filter, Serializable {
                  @Override
                  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }
              }
              """,
            """
              import org.springframework.web.filter.OncePerRequestFilter;

              import javax.servlet.FilterChain;
              import javax.servlet.ServletException;
              import javax.servlet.ServletRequest;
              import javax.servlet.ServletResponse;
              import javax.servlet.http.*;
              import java.io.IOException;
              import java.io.Serializable;

              public class CustomFilter extends OncePerRequestFilter implements Serializable {
                  @Override
                  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                          throws IOException, ServletException {
                      chain.doFilter(request, response);
                  }
              }
              """
          )
        );
    }
}
