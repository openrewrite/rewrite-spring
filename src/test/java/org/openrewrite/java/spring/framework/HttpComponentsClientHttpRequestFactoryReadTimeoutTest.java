package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HttpComponentsClientHttpRequestFactoryReadTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0")
                .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-context-5", "spring-web-5", "spring-boot-3.1", "httpclient-4", "httpcore-4", "spring-beans-5"));
    }

    @Test
    @DocumentExample
    void migrateHttpComponentsClientHttpRequestFactoryReadTimeout() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.config.Registry;
              import org.apache.http.config.RegistryBuilder;
              import org.apache.http.conn.socket.ConnectionSocketFactory;
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestClientException;
              import org.springframework.web.client.RestTemplate;

              import java.security.KeyManagementException;
              import java.security.KeyStoreException;
              import java.security.NoSuchAlgorithmException;
              import java.time.Duration;

              @Configuration
              public class RestContextInitializer {
                  private final Duration readTimeout = Duration.ofSeconds(30);
                  private final int maxConnections = 1;

                  @Bean
                  public RestTemplate getRestTemplate() {
                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout((int) readTimeout.toMillis());
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }

                  private PoolingHttpClientConnectionManager createConnectionManager() {
                      try {
                          Registry<ConnectionSocketFactory> socketFactoryRegistry =
                                  RegistryBuilder.<ConnectionSocketFactory>create()
                                          .build();
                          PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                          poolingConnectionManager.setMaxTotal(maxConnections);
                          return poolingConnectionManager;
                      } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                          throw new RestClientException(e.getMessage(), e);
                      }
                  }
              }
              """,
            """
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestClientException;
              import org.springframework.web.client.RestTemplate;

              import java.security.KeyManagementException;
              import java.security.KeyStoreException;
              import java.security.NoSuchAlgorithmException;
              import java.time.Duration;
              import java.util.concurrent.TimeUnit;

              @Configuration
              public class RestContextInitializer {
                  private final Duration readTimeout = Duration.ofSeconds(30);
                  private final int maxConnections = 1;

                  @Bean
                  public RestTemplate getRestTemplate() {
                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }

                  private PoolingHttpClientConnectionManager createConnectionManager() {
                      try {
                          SocketConfig socketConfig = SocketConfig.custom()
                                  .setSoTimeout((int)readTimeout.toSeconds(), TimeUnit.SECONDS)
                                  .build();
                          return PoolingHttpClientConnectionManagerBuilder
                                  .create()
                                  .setMaxConnTotal(maxConnections)
                                  .setDefaultSocketConfig(socketConfig)
                                  .build();
                      } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                          throw new RestClientException(e.getMessage(), e);
                      }
                  }
              }
              """
          )
        );
    }

}