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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddConfigurationAnnotationIfBeansPresentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddConfigurationAnnotationIfBeansPresent())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-autoconfigure-3",
              "spring-boot-3.5",
              "spring-beans-6",
              "spring-context-6.2",
              "spring-web-6.2",
              "spring-core-6",
              "spring-security-core-6",
              "spring-security-config-6.2",
              "spring-cloud-openfeign-core"));
    }

    @Test
    void privateBeanMethod() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Utility {
                  @Bean private Object bean() { return new Object(); }
              }
              """
          )
        );
    }

    @Test
    void finalBeanMethod() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Utility {
                  @Bean final Object bean() { return new Object(); }
              }
              """
          )
        );
    }

    @Test
    void privateBeanAfterOverridableBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class Utility {
                  @Bean Object first() { return new Object(); }
                  @Bean private Object second() { return new Object(); }
              }
              """
          )
        );
    }

    @Test
    void finalClassWithBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              final class Utility {
                  @Bean Object bean() { return new Object(); }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void enableWebSecurityWithBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @EnableWebSecurity
              class A {
                @Bean String hello() { return "hello"; }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @Configuration
              @EnableWebSecurity
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void enableWebSecurityNoBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @EnableWebSecurity
              class A {}
              """
          )
        );
    }

    @Test
    void configurationMetaAnnotation() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.context.annotation.Bean;

              @SpringBootApplication
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void abstractClassWithBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              abstract class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void noAnnotationsWithBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class A {
                @Bean String hello() { return "hello"; }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void feignClientScopedConfiguration() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class MyFeignConfiguration {
                  @Bean
                  public String authInterceptor() {
                      return "interceptor";
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;

              @FeignClient(name = "my-service", configuration = MyFeignConfiguration.class)
              public interface MyFeignClient {
              }
              """
          )
        );
    }

    @Test
    void feignClientScopedConfigurationArray() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class FeignConfigA {
                  @Bean String a() { return "a"; }
              }
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class FeignConfigB {
                  @Bean String b() { return "b"; }
              }
              """
          ),
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;

              @FeignClient(name = "svc", configuration = {FeignConfigA.class, FeignConfigB.class})
              public interface SvcClient {
              }
              """
          )
        );
    }

    @Test
    void enableFeignClientsDefaultConfiguration() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class DefaultFeignConfig {
                  @Bean String defaultBean() { return "default"; }
              }
              """
          ),
          java(
            """
              import org.springframework.cloud.openfeign.EnableFeignClients;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              @EnableFeignClients(defaultConfiguration = DefaultFeignConfig.class)
              public class App {
              }
              """
          )
        );
    }

    @Test
    void beansClassNotReferencedStillGetsConfiguration() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class UsedElsewhereConfig {
                  @Bean String bean() { return "bean"; }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              public class UsedElsewhereConfig {
                  @Bean String bean() { return "bean"; }
              }
              """
          ),
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;

              @FeignClient(name = "unrelated")
              public interface UnrelatedClient {
              }
              """
          )
        );
    }
}
