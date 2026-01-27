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

import static org.openrewrite.java.Assertions.java;

class RemovedServletViewSupportTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.RemovedServletViewSupport")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-webmvc-5", "spring-core-5", "spring-web-5", "spring-context-5"));
    }

    @DocumentExample
    @Test
    void findAbstractPdfView() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.servlet.view.document.AbstractPdfView;

              public abstract class MyPdfView extends AbstractPdfView {
              }
              """,
            """
              import org.springframework.web.servlet.view.document.AbstractPdfView;

              public abstract class MyPdfView extends /*~~>*/AbstractPdfView {
              }
              """
          )
        );
    }

    @Test
    void findAbstractXlsxView() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.servlet.view.document.AbstractXlsxView;

              public abstract class MyExcelView extends AbstractXlsxView {
              }
              """,
            """
              import org.springframework.web.servlet.view.document.AbstractXlsxView;

              public abstract class MyExcelView extends /*~~>*/AbstractXlsxView {
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotUsingDeprecatedViews() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.servlet.view.AbstractView;

              public abstract class MyCustomView extends AbstractView {
              }
              """
          )
        );
    }
}
