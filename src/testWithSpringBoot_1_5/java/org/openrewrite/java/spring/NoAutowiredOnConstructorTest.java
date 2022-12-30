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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"MissortedModifiers"})
class NoAutowiredOnConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new NoAutowiredOnConstructor())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeLeadingAutowiredAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Component;
              
              @Autowired
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  @Autowired
                  public TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              
              @Component
              public class TestSourceB {
              }
              
              @Component
              public class TestSourceC {
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Component;
              
              @Autowired
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  public TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              
              @Component
              public class TestSourceB {
              }
              
              @Component
              public class TestSourceC {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeLeadingAutowiredAnnotationNoModifiers() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Component;
              
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  @Autowired
                  TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              
              @Component
              public class TestSourceB {
              }
              
              @Component
              public class TestSourceC {
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Component;
              
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              
              @Component
              public class TestSourceB {
              }
              
              @Component
              public class TestSourceC {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeAutowiredWithMultipleAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              import org.springframework.beans.factory.annotation.Required;
              import org.springframework.stereotype.Component;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Autowired
                  @Required
                  @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Required
                  @Autowired
                  @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Required
                  @Qualifier
                  @Autowired
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              import org.springframework.beans.factory.annotation.Required;
              import org.springframework.stereotype.Component;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Required
                  @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Required
                  @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Required
                  @Qualifier
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeAutowiredWithMultipleInLineAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              import org.springframework.beans.factory.annotation.Required;
              import org.springframework.stereotype.Component;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Autowired @Required @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Required @Autowired @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Required @Qualifier @Autowired
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              import org.springframework.beans.factory.annotation.Required;
              import org.springframework.stereotype.Component;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Required @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Required @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Required @Qualifier
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              
              @Component
              public class TestSourceA {
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void oneNamePrefixAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @Autowired DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos1() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos2() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @SuppressWarnings("") @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos3() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @SuppressWarnings("") @Deprecated @Autowired DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
                  
                  public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void keepAutowiredAnnotationsWhenMultipleConstructorsExist() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.core.io.Resource;
              import java.io.PrintStream;
              
              public class MyAppResourceService {
                  private final Resource someResource;
                  private final PrintStream printStream;
              
                  public MyAppResourceService(Resource someResource) {
                      this.someResource = someResource;
                      this.printStream = System.out;
                  }
              
                  @Autowired
                  public MyAppResourceService(Resource someResource, PrintStream printStream) {
                      this.someResource = someResource;
                      this.printStream = printStream;
                  }
              }
              """
          )
        );
    }

    @Test
    void optionalAutowiredAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;

                  public DatabaseConfiguration(@Autowired(required = false) DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Test
    void noAutowiredAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;

                  @Primary
                  public DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }
}
