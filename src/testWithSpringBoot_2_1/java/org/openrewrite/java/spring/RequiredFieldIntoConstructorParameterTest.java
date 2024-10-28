package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RequiredFieldIntoConstructorParameterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RequiredFieldIntoConstructorParameter())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
    }

    @Test
    void fieldIntoExistingSingleConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package demo;
              
              import org.springframework.beans.factory.annotation.Required;
              
              public class Test {
                  private String a;
              
                  @Required
                  void setA(String a) {
                      this.a = a;
                  }
              }
              """,
            """
              package demo;
              
              public class Test {
                  private final String a;
    
                  Test(String a) {
                      this.a = a;
                  }
              }
              """
          )
        );
    }

    @Test
    public void requiredFieldIntoConstructorParameter() {
        rewriteRun(
          //language=java
          java(
            """
              package demo;
              import org.springframework.beans.factory.annotation.Required;
              
              public class Test {
                 private String first;
                 private String a;
              
                 Test(String first, String a) {
                    this.first = first;
                  }
              
                 @Required
                 void setA(String a) {
                     this.a = a;
                 }
              }
              """,
            """
              package demo;
              
              public class Test {
                  private final String a;
                  private String first;
              
                  Test(String first, String a) {
                      this.first = first;
                      this.a = a;
                  }
              }
              """
          )
        );
    }
}
