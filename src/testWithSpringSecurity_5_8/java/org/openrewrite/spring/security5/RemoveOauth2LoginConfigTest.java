package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security6.RemoveOauth2LoginConfig;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveOauth2LoginConfigTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveOauth2LoginConfig())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),"spring-context-5.3.+", "spring-beans-5.3.+", "spring-web-5.3.+", "spring-security-web-5.8.+", "spring-security-config-5.8.+"));
    }

    @Test
    void removeUnneededConfigFromACallChain() {
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(3),
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint()
                              .userAuthoritiesMapper(null);
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated());
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUserInfoEndpointStatement() {
        rewriteRun(
          spec -> spec.cycles(2).expectedCyclesThatMakeChanges(2),
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity> auth2 = http.oauth2Login();
                      auth2.userInfoEndpoint()
                          .userAuthoritiesMapper(null);
                      return http.build();
                  }
              }
              """,
            """

              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity> auth2 = http.oauth2Login();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUserAuthoritiesMapperStatement() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity>.UserInfoEndpointConfig x = http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint();
                      x.userAuthoritiesMapper(null);
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
              import org.springframework.security.core.GrantedAuthority;
              import org.springframework.security.core.authority.SimpleGrantedAuthority;
              import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
              import org.springframework.security.web.SecurityFilterChain;

              import java.util.HashSet;
              import java.util.Set;

              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      OAuth2LoginConfigurer<HttpSecurity>.UserInfoEndpointConfig x = http.authorizeHttpRequests(authorize -> authorize
                              .requestMatchers("/public", "/public/*").permitAll()
                              .requestMatchers("/login").permitAll()
                              .anyRequest().authenticated())
                          .oauth2Login()
                          .userInfoEndpoint();
                      return http.build();
                  }
              }
              """
          )
        );
    }
}
