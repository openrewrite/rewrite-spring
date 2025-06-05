/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FieldInjectionToConstructorInjectionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FieldInjectionToConstructorInjection(null))
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            //language=java
            .dependsOn(
              """
                package org.springframework.beans.factory.annotation;
                public @interface Autowired {
                    boolean required() default true;
                }
                """,
              """
                package org.springframework.beans.factory.annotation;
                public @interface Qualifier {
                    String value();
                }
                """,
              """
                package com.example;
                public class UserRepository {}
                """,
              """
                package com.example;
                public class EmailService {}
                """,
              """
                package com.example;
                public class PaymentProcessor {}
                """,
              """
                package com.example;
                public class MessageSender {}
                """,
              """
                package com.example;
                public class Logger {}
                """,
              """
                package com.example;
                public class CacheManager {}
                """,
              """
                package com.example;
                public class ConfigRepository {}
                """,
              """
                package com.example;
                public class DataRepository {}
                """,
              """
                package com.example;
                public class BaseService {}
                """,
              """
                package com.example;
                public class SpecialRepository {}
                """,
              """
                package com.example;
                public class ServiceA {}
                """,
              """
                package com.example;
                public class ServiceB {}
                """,
              """
                package com.example;
                public class ServiceC {}
                """
            )
            .classpathFromResources(new InMemoryExecutionContext(), "spring-context-5.+", "spring-beans-5.+"));
    }

    @DocumentExample
    @Test
    void singleAutowiredField() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class UserService {

                  @Autowired
                  private UserRepository userRepository;

                  private void foo() {
                  }

              }
              """,
            """
              package com.example;

              public class UserService {


                  private final UserRepository userRepository;

                  public UserService(UserRepository userRepository) {
                      this.userRepository = userRepository;
                  }

                  private void foo() {
                  }

              }
              """
          )
        );
    }

    @Test
    void multipleAutowiredFields() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class UserService {

                  @Autowired
                  private UserRepository userRepository;

                  @Autowired
                  private EmailService emailService;

              }
              """,
            """
              package com.example;

              public class UserService {

                  private final UserRepository userRepository;

                  private final EmailService emailService;

                  public UserService(UserRepository userRepository, EmailService emailService) {
                      this.userRepository = userRepository;
                      this.emailService = emailService;
                  }

              }
              """
          )
        );
    }

    @Test
    void fieldWithQualifierAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;

              public class PaymentService {

                  @Autowired
                  @Qualifier("primaryProcessor")
                  private PaymentProcessor paymentProcessor;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Qualifier;

              public class PaymentService {

                  private final PaymentProcessor paymentProcessor;

                  public PaymentService(@Qualifier("primaryProcessor") PaymentProcessor paymentProcessor) {
                      this.paymentProcessor = paymentProcessor;
                  }

              }
              """
          )
        );
    }

    @Test
    void multipleFieldsWithQualifiers() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;

              public class NotificationService {

                  @Autowired
                  @Qualifier("emailSender")
                  private MessageSender emailSender;

                  @Autowired
                  @Qualifier("smsSender")
                  private MessageSender smsSender;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Qualifier;

              public class NotificationService {

                  private final MessageSender emailSender;

                  private final MessageSender smsSender;

                  public NotificationService(@Qualifier("emailSender") MessageSender emailSender, @Qualifier("smsSender") MessageSender smsSender) {
                      this.emailSender = emailSender;
                      this.smsSender = smsSender;
                  }

              }
              """
          )
        );
    }

    @Test
    void existingEmptyConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class LoggingService {

                  @Autowired
                  private Logger logger;

                  public LoggingService() {
                      // Empty constructor
                  }

              }
              """,
            """
              package com.example;

              public class LoggingService {

                  private final Logger logger;

                  public LoggingService(Logger logger) {
                      this.logger = logger;
                  }

              }
              """
          )
        );
    }

    @Test
    void skipClassWithExistingParameterizedConstructor() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class ConfigService {

                  @Autowired
                  private ConfigRepository configRepository;

                  public ConfigService(String configPath) {
                      // Constructor with parameters
                  }

              }
              """
          )
        );
    }

    @Test
    void skipClassWithMultipleConstructors() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class DataService {

                  @Autowired
                  private DataRepository dataRepository;

                  public DataService() {
                      // Default constructor
                  }

                  public DataService(boolean initialize) {
                      // Another constructor
                  }

              }
              """
          )
        );
    }

    @Test
    void skipClassThatExtendsAnotherClass() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class SpecializedService extends BaseService {

                  @Autowired
                  private SpecialRepository repository;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class SpecializedService extends BaseService {

                  @Autowired
                  private SpecialRepository repository;

              }
              """
          )
        );
    }

    @Test
    void skipClassWithTooManyAutowiredFields() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new FieldInjectionToConstructorInjection(2)),
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class ComplexService {

                  @Autowired
                  private ServiceA serviceA;

                  @Autowired
                  private ServiceB serviceB;

                  @Autowired
                  private ServiceC serviceC;

              }
              """
          )
        );
    }

    @Test
    void fieldWithFinalModifier() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              public class CacheService {

                  @Autowired
                  private final CacheManager cacheManager = null;

              }
              """,
            """
              package com.example;

              public class CacheService {

                  private final CacheManager cacheManager;

                  public CacheService(CacheManager cacheManager) {
                      this.cacheManager = cacheManager;
                  }

              }
              """
          )
        );
    }
}
