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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"RedundantThrows", "UnnecessaryLocalVariable"})
class HttpComponentsClientHttpRequestFactoryReadTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HttpComponentsClientHttpRequestFactoryReadTimeout())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-5.3",
            "spring-boot-3",
            "spring-web-5.3",
            "httpclient5",
            "httpcore5"));
    }

    @Test
    @DocumentExample
    void migrateHttpComponentsClientHttpRequestFactoryReadTimeout() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import java.util.concurrent.TimeUnit;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(30000, TimeUnit.MILLISECONDS).build());

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migratePoolingHttpClientConnectionManagerBuilderToVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
              import org.apache.hc.core5.ssl.SSLContexts;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import javax.net.ssl.SSLContext;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (cert, authType) -> true).build();
                      SSLConnectionSocketFactory socketFactoryRegistry = new SSLConnectionSocketFactory(sslContext,NoopHostnameVerifier.INSTANCE);
                      PoolingHttpClientConnectionManager poolingConnectionManager = PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(socketFactoryRegistry).build();

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.core5.ssl.SSLContexts;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import javax.net.ssl.SSLContext;

              import java.util.concurrent.TimeUnit;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (cert, authType) -> true).build();
                      SSLConnectionSocketFactory socketFactoryRegistry = new SSLConnectionSocketFactory(sslContext,NoopHostnameVerifier.INSTANCE);
                      PoolingHttpClientConnectionManager poolingConnectionManager = PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(socketFactoryRegistry).build();
                      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(30000, TimeUnit.MILLISECONDS).build());

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMigrateWhenUsingPoolingHttpClientConnectionManagerBuilderInline() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
              import org.apache.hc.core5.ssl.SSLContexts;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import javax.net.ssl.SSLContext;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (cert, authType) -> true).build();
                      SSLConnectionSocketFactory socketFactoryRegistry = new SSLConnectionSocketFactory(sslContext,NoopHostnameVerifier.INSTANCE);
                      return PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(socketFactoryRegistry).build();

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
              import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
              import org.apache.hc.core5.ssl.SSLContexts;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import javax.net.ssl.SSLContext;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (cert, authType) -> true).build();
                      SSLConnectionSocketFactory socketFactoryRegistry = new SSLConnectionSocketFactory(sslContext,NoopHostnameVerifier.INSTANCE);
                      return PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(socketFactoryRegistry).build();

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMigrateWhenNoIntermediateVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMigrateWhenUsingBasicHttpClientConnectionManager() {
        rewriteRun(
          //language=java
          java(
            """

              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      BasicHttpClientConnectionManager basicConnectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set basicConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """

              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      BasicHttpClientConnectionManager basicConnectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set basicConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMigrateWhenNoHttpClientConnectionManager() {
        rewriteRun(
          //language=java
          java(
            """

              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """

              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotDuplicateSetDefaultSocketConfig() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().build());

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().build());

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateHttpComponentsClientHttpRequestFactoryReadTimeoutLocalVar() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  int timeout = 30000;
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(timeout);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  int timeout = 30000;
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)
                                  clientHttpRequestFactory.setReadTimeout(timeout);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }
}
