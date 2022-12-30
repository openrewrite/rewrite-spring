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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ReplaceDeprecatedEnvironmentTestUtilsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDeprecatedEnvironmentTestUtils())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans","spring-core","spring-context","spring-boot-test","spring-web"));
    }

    @Test
    void givenHasStringVariableWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      String pair = "pair";
                      addEnvironment(context, pair);
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      String pair = "pair";
                      TestPropertyValues.of(pair).applyTo(context);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenHasSingleStringWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      addEnvironment(context, "pair:pair");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("pair:pair").applyTo(context);
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    void givenConstructsStringAndContextWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      addEnvironment(new AnnotationConfigApplicationContext(), "pair" + "pair");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      TestPropertyValues.of("pair" + "pair").applyTo(new AnnotationConfigApplicationContext());
                  }
              }
              """
          )
        );
    }

    @Test
    void givenChainedCallsReplacesThemWithAFluentSetOfCalls() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      addEnvironment(context, "key1:value1");
                      addEnvironment(context, "key2:value2");
                      addEnvironment(context, "key3:value3");
                      String x = "x";
                      addEnvironment(context, "key4:value4");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("key1:value1").and("key2:value2").and("key3:value3").applyTo(context);
                      String x = "x";
                      TestPropertyValues.of("key4:value4").applyTo(context);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenChainedCallsWithDifferentContextsCoalescesThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = new AnnotationConfigApplicationContext();
                      addEnvironment(context2, "key1:value1");
                      addEnvironment(context1, "key2:value2");
                      addEnvironment(context1, "key3:value3");
                      String x = "x";
                      addEnvironment(context2, "key4:value4");
                      addEnvironment(context2, "key5:value5");
                      addEnvironment(context1, "key6:value6");
                      addEnvironment(context2, "key7:value7");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("key1:value1").applyTo(context2);
                      TestPropertyValues.of("key2:value2").and("key3:value3").applyTo(context1);
                      String x = "x";
                      TestPropertyValues.of("key4:value4").and("key5:value5").applyTo(context2);
                      TestPropertyValues.of("key6:value6").applyTo(context1);
                      TestPropertyValues.of("key7:value7").applyTo(context2);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenChainedCallsWithGeneratedContextsCoalescesThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      addEnvironment(new AnnotationConfigApplicationContext(), "key1:value1");
                      addEnvironment(new AnnotationConfigApplicationContext(), "key2:value2");
                      addEnvironment(new AnnotationConfigApplicationContext(), "key3:value3");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      TestPropertyValues.of("key1:value1").applyTo(new AnnotationConfigApplicationContext());
                      TestPropertyValues.of("key2:value2").applyTo(new AnnotationConfigApplicationContext());
                      TestPropertyValues.of("key3:value3").applyTo(new AnnotationConfigApplicationContext());
                  }
              }
              """
          )
        );
    }

    @Test
    void givenChainedCallsWithMixOfContextsCoalescesThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      addEnvironment(new AnnotationConfigApplicationContext(), "key1:value1");
                      addEnvironment(new AnnotationConfigApplicationContext(), "key2:value2");
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      addEnvironment(context1, "key3:value3");
                      addEnvironment(context1, "key4:value4");
                      addEnvironment(new AnnotationConfigApplicationContext(), "key5:value5");
                      addEnvironment(context1, "key5:value5");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      TestPropertyValues.of("key1:value1").applyTo(new AnnotationConfigApplicationContext());
                      TestPropertyValues.of("key2:value2").applyTo(new AnnotationConfigApplicationContext());
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("key3:value3").and("key4:value4").applyTo(context1);
                      TestPropertyValues.of("key5:value5").applyTo(new AnnotationConfigApplicationContext());
                      TestPropertyValues.of("key5:value5").applyTo(context1);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenChainedCallsThatReferToTheSameObjectUnfortunatelyDoesntChainThem() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = context1;
                      addEnvironment(context2, "key1:value1");
                      addEnvironment(context1, "key2:value2");
                      addEnvironment(context1, "key3:value3");
                      String x = "x";
                      addEnvironment(context2, "key4:value4");
                      addEnvironment(context2, "key5:value5");
                      context1 = new AnnotationConfigApplicationContext();
                      addEnvironment(context1, "key6:value6");
                      addEnvironment(context2, "key7:value7");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = context1;
                      TestPropertyValues.of("key1:value1").applyTo(context2);
                      TestPropertyValues.of("key2:value2").and("key3:value3").applyTo(context1);
                      String x = "x";
                      TestPropertyValues.of("key4:value4").and("key5:value5").applyTo(context2);
                      context1 = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("key6:value6").applyTo(context1);
                      TestPropertyValues.of("key7:value7").applyTo(context2);
                  }
              }
              """
          )
        );
    }


    @Test
    void givenEnvironmentAddEnvironmentChainsThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.web.context.support.StandardServletEnvironment;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      StandardServletEnvironment environment1 = new StandardServletEnvironment();
                      StandardServletEnvironment environment2 = new StandardServletEnvironment();
                      addEnvironment(environment2, "key1:value1");
                      addEnvironment(environment1, "key5:value5");
                      String x = "x";
                      addEnvironment(environment2, "key6:value6");
                      addEnvironment(environment2, "key7:value7");
                      environment1 = new StandardServletEnvironment();
                      addEnvironment(environment1, "key8:value8");
                      addEnvironment(environment2, "key9:value9");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.web.context.support.StandardServletEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      StandardServletEnvironment environment1 = new StandardServletEnvironment();
                      StandardServletEnvironment environment2 = new StandardServletEnvironment();
                      TestPropertyValues.of("key1:value1").applyTo(environment2);
                      TestPropertyValues.of("key5:value5").applyTo(environment1);
                      String x = "x";
                      TestPropertyValues.of("key6:value6").and("key7:value7").applyTo(environment2);
                      environment1 = new StandardServletEnvironment();
                      TestPropertyValues.of("key8:value8").applyTo(environment1);
                      TestPropertyValues.of("key9:value9").applyTo(environment2);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenNamedEnvironmentAddEnvironmentChainsThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.web.context.support.StandardServletEnvironment;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      StandardServletEnvironment environment = new StandardServletEnvironment();
                      addEnvironment(environment, "key1:value1");
                      addEnvironment("test", environment, "key5:value5");
                      String x = "x";
                      addEnvironment("test", environment, "key6:value6");
                      addEnvironment("test", environment, "key7:value7");
                      environment = new StandardServletEnvironment();
                      addEnvironment(environment, "key8:value8");
                      addEnvironment(environment, "key9:value9");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.web.context.support.StandardServletEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      StandardServletEnvironment environment = new StandardServletEnvironment();
                      TestPropertyValues.of("key1:value1").applyTo(environment);
                      TestPropertyValues.of("key5:value5").applyTo(environment, TestPropertyValues.Type.MAP, "test");
                      String x = "x";
                      TestPropertyValues.of("key6:value6").and("key7:value7").applyTo(environment, TestPropertyValues.Type.MAP, "test");
                      environment = new StandardServletEnvironment();
                      TestPropertyValues.of("key8:value8").and("key9:value9").applyTo(environment);
                  }
              }
              """
          )
        );
    }

    @Test
    void givenVarargsAddEnvironmentChainsThemCorrectly() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              package com.mycompany;
              
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = context1;
                      addEnvironment(context2, "key1:value1", "key2:value2");
                      addEnvironment(context1, "key3:value3", "key4:value4");
                      addEnvironment(context1, "key5:value5");
                      String x = "x";
                      addEnvironment(context2, "key6:value6");
                      addEnvironment(context2, "key7:value7");
                      context1 = new AnnotationConfigApplicationContext();
                      addEnvironment(context1, "key8:value8");
                      addEnvironment(context2, "key9:value9");
                  }
              }
              """,
            """
              package com.mycompany;
              
              import org.springframework.boot.test.util.TestPropertyValues;
              import org.springframework.context.annotation.AnnotationConfigApplicationContext;
              
              public class MyClass {
                  public void myMethod() {
                      AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                      AnnotationConfigApplicationContext context2 = context1;
                      TestPropertyValues.of("key1:value1").and("key2:value2").applyTo(context2);
                      TestPropertyValues.of("key3:value3").and("key4:value4").and("key5:value5").applyTo(context1);
                      String x = "x";
                      TestPropertyValues.of("key6:value6").and("key7:value7").applyTo(context2);
                      context1 = new AnnotationConfigApplicationContext();
                      TestPropertyValues.of("key8:value8").applyTo(context1);
                      TestPropertyValues.of("key9:value9").applyTo(context2);
                  }
              }
              """
          )
        );
    }
}
