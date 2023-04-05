package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.UpdateRequestCache;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UpdateRequestCacheTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateRequestCache())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @Test
    void security5Default() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
              import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login(oauth2 -> oauth2
                              .failureHandler(new SimpleUrlAuthenticationFailureHandler("/auth-error")))
                          .requestCache((cache) -> cache
                              .requestCache(requestCache));

                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
              import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
                  
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
                      requestCache.setMatchingRequestParameterName("continue");
    
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login(oauth2 -> oauth2
                              .failureHandler(new SimpleUrlAuthenticationFailureHandler("/auth-error")))
                          .requestCache((cache) -> cache
                              .requestCache(requestCache));
    
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void NoChangeIfContinueParameterHasBeenSet() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
              import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
                      requestCache.setMatchingRequestParameterName("continue");

                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login(oauth2 -> oauth2
                              .failureHandler(new SimpleUrlAuthenticationFailureHandler("/auth-error")))
                          .requestCache((cache) -> cache
                                  .requestCache(requestCache));

                      return http.build();
                  }
              }
              """)
        );
    }

    @Test
    void HttpSessionRequestCacheConstructorAsParameter() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
              import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                    http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/public", "/public/*").permitAll()
                            .requestMatchers("/login").permitAll()
                            .anyRequest().authenticated())
                        .oauth2Login(oauth2 -> oauth2
                            .failureHandler(new SimpleUrlAuthenticationFailureHandler("/auth-error")))
                        .requestCache((cache) -> cache
                            .requestCache(new HttpSessionRequestCache()));
                          
                    return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
              import org.springframework.security.web.savedrequest.NullRequestCache;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {

                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                    http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/public", "/public/*").permitAll()
                            .requestMatchers("/login").permitAll()
                            .anyRequest().authenticated())
                        .oauth2Login(oauth2 -> oauth2
                            .failureHandler(new SimpleUrlAuthenticationFailureHandler("/auth-error")))
                        .requestCache((cache) -> cache
                            .requestCache(new NullRequestCache()));
                          
                    return http.build();
                  }
              }
              """
          )
        );
    }
}
