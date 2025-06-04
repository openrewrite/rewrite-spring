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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FieldInjectionToConstructorInjectionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FieldInjectionToConstructorInjectionSimple(null))
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
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
                  private String userRepository;

              }
              """,
            """
              package com.example;

              public class UserService {

                  private final String userRepository;

                  public UserService(String userRepository) {
                      this.userRepository = userRepository;
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
                  private String userRepository;

                  @Autowired
                  private Integer emailService;

              }
              """,
            """
              package com.example;

              public class UserService {

                  private final String userRepository;

                  private final Integer emailService;

                  public UserService(String userRepository, Integer emailService) {
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
                  private String paymentProcessor;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Qualifier;

              public class PaymentService {

                  private final String paymentProcessor;

                  public PaymentService(@Qualifier("primaryProcessor") String paymentProcessor) {
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
                  private String emailSender;

                  @Autowired
                  @Qualifier("smsSender")
                  private String smsSender;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Qualifier;

              public class NotificationService {

                  private final String emailSender;

                  private final String smsSender;

                  public NotificationService(@Qualifier("emailSender") String emailSender, @Qualifier("smsSender") String smsSender) {
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
                  private String logger;

                  public LoggingService() {
                      // Empty constructor
                  }

              }
              """,
            """
              package com.example;

              public class LoggingService {

                  private final String logger;

                  public LoggingService(String logger) {
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
                  private String configRepository;

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
                  private String dataRepository;

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

              class BaseService {}

              public class SpecializedService extends BaseService {

                  @Autowired
                  private String repository;

              }
              """,
            """
              package com.example;

              import org.springframework.beans.factory.annotation.Autowired;

              class BaseService {}

              public class SpecializedService extends BaseService {

                  @Autowired
                  private String repository;

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
                  private String serviceA;

                  @Autowired
                  private Integer serviceB;

                  @Autowired
                  private Boolean serviceC;

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
                  private final String cacheManager = null;

              }
              """,
            """
              package com.example;

              public class CacheService {

                  private final String cacheManager;

                  public CacheService(String cacheManager) {
                      this.cacheManager = cacheManager;
                  }

              }
              """
          )
        );
    }
}
