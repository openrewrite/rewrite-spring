/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.DocumentExample;
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

    @DocumentExample
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
