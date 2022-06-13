/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

/**
 * @author Alex Boyko
 */
class WebSecurityConfigurerAdapterRecipeTest : JavaRecipeTest {

    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-security", "spring-web", "tomcat-embed")
            .build()

    override val recipe: Recipe
        get() = WebSecurityConfigurerAdapterRecipe()

    @Test
    fun configureHttpSecurityMethod() = assertChanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
            }
        """,
        after = """
            import static org.springframework.security.config.Customizer.withDefaults;

            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.web.SecurityFilterChain;
          
            @Configuration
            public class SecurityConfiguration {
            
                @Bean
                SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                    return http.build();
                }
            
            }
        """
    )

    @Test
    fun noConfigurationAnnotation() = assertUnchanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
            }
        """
    )

    @Test
    fun configureWebSecurityMethod() = assertChanged(
        before = """
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.WebSecurity;

            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

                @Override
                public void configure(WebSecurity web) {
                    web.ignoring().antMatchers("/ignore1", "/ignore2");
                }

            }
        """,
        after = """
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.WebSecurity;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

            @Configuration
            public class SecurityConfiguration {

                @Bean
                WebSecurityCustomizer webSecurityCustomizer() {
                    return (web) -> {
                        web.ignoring().antMatchers("/ignore1", "/ignore2");
                    };
                }

            }
        """
    )

    @Test
    fun configurAuthManagerMethod() = assertUnchanged(
        before = """
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
            import org.springframework.security.ldap.userdetails.PersonContextMapper;

            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

                @Override
                protected void configure(AuthenticationManagerBuilder auth) {
                    auth
                        .ldapAuthentication()
                        .userDetailsContextMapper(new PersonContextMapper())
                        .userDnPatterns("uid={0},ou=people")
                        .contextSource()
                        .port(0);
                }
    
            }
        """
    )

    @Test
    fun overideUnapplicableMethod() = assertUnchanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.authentication.AuthenticationManager;
;
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
                
                @Override
                protected AuthenticationManager authenticationManager() throws Exception {
                    throw new Exception("Not Implemented");
                }
            
            }
        """
    )

    @Test
    fun unapplicableMethodInvocation() = assertUnchanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) {
                    System.out.println(getApplicationContext());
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
                
            }
        """
    )

    @Test
    fun configureHttpSecurityMethodInnerClass() = assertChanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    System.out.println(getApplicationContext());
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
                @Configuration
                public static class InnerSecurityConfiguration extends WebSecurityConfigurerAdapter {
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        http
                            .authorizeHttpRequests((authz) -> authz
                                .anyRequest().authenticated()
                            )
                            .httpBasic(withDefaults());
                    }
                }
            }
        """,
        after = """
            import static org.springframework.security.config.Customizer.withDefaults;

            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.web.SecurityFilterChain;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;

            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    System.out.println(getApplicationContext());
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
                @Configuration
                public static class InnerSecurityConfiguration {
                    @Bean
                    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        http
                            .authorizeHttpRequests((authz) -> authz
                                .anyRequest().authenticated()
                            )
                            .httpBasic(withDefaults());
                        return http.build();
                    }
                }
            }
        """
    )

    @Test
    fun configureHttpSecurityMethodInnerClassWithNotApplicableMethod() = assertChanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
                @Configuration
                public static class InnerSecurityConfiguration extends WebSecurityConfigurerAdapter {
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        System.out.println(getApplicationContext());
                        http
                            .authorizeHttpRequests((authz) -> authz
                                .anyRequest().authenticated()
                            )
                            .httpBasic(withDefaults());
                    }
                }
            }
        """,
        after = """
            import static org.springframework.security.config.Customizer.withDefaults;

            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.web.SecurityFilterChain;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;

            @Configuration
            public class SecurityConfiguration {
            
                @Bean
                SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                    return http.build();
                }
            
                @Configuration
                public static class InnerSecurityConfiguration extends WebSecurityConfigurerAdapter {
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        System.out.println(getApplicationContext());
                        http
                            .authorizeHttpRequests((authz) -> authz
                                .anyRequest().authenticated()
                            )
                            .httpBasic(withDefaults());
                    }
                }
            }
        """
    )

    @Test
    fun configureHttpSecurityMethodWithNotApplicableMethodInNonStaticInnerClass() = assertUnchanged(
        before = """
            import static org.springframework.security.config.Customizer.withDefaults;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            
            @Configuration
            public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
            
                @Override
                protected void configure(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests((authz) -> authz
                            .anyRequest().authenticated()
                        )
                        .httpBasic(withDefaults());
                }
            
                @Configuration
                public class InnerSecurityConfiguration {
                    protected void configure() throws Exception {
                        System.out.println(getApplicationContext());
                    }
                }
            }
        """
    )
}
