/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.UseNewRequestMatchers;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseNewRequestMatchersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseNewRequestMatchers())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-context", "spring-beans", "spring-web", "spring-security-web", "spring-security-config"));
    }

    @Test
    void migratesMvcMatchersWithMvcPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .mvcMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers("/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesMvcMatchersWithMvcPatternsAndHttpMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .mvcMatchers(HttpMethod.GET, "/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.http.HttpMethod;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers(HttpMethod.GET, "/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesRegexMatchersWithRegexPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .regexMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers("/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesRegexMatchersWithRegexPatternsAndHttpMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .regexMatchers(HttpMethod.GET, "/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.http.HttpMethod;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers(HttpMethod.GET, "/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesAntMatchersWithAntPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .antMatchers("/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers("/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesAntMatchersWithHttpMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .antMatchers(HttpMethod.GET).permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.http.HttpMethod;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers(HttpMethod.GET).permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesAntMatchersWithAntPatternsAndHttpMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .antMatchers(HttpMethod.GET, "/static/**").permitAll()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.http.HttpMethod;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers(HttpMethod.GET, "/static/**").permitAll()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

    @Test
    void migratesAllTypesOfMatchers() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.http.HttpMethod;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              @EnableWebSecurity
              public class SecurityConfig {
                  @Bean
                  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeHttpRequests((authz) -> authz
                                      .regexMatchers("/api/admin/**").hasRole("ADMIN")
                                      .antMatchers(HttpMethod.GET, "/api/user/**").hasRole("USER")
                                      .mvcMatchers(HttpMethod.PATCH).denyAll()
                                      .anyRequest().authenticated()
                              );
                      return http.build();
                  }
                  
              }
                            """
            ,
            """
            package com.example;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.http.HttpMethod;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeHttpRequests((authz) -> authz
                                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                    .requestMatchers(HttpMethod.GET, "/api/user/**").hasRole("USER")
                                    .requestMatchers(HttpMethod.PATCH).denyAll()
                                    .anyRequest().authenticated()
                            );
                    return http.build();
                }
            
            }
                        """
          )
        );
    }

}