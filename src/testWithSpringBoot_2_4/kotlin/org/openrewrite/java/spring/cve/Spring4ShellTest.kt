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
package org.openrewrite.java.spring.cve

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.cve.Spring4Shell

class Spring4ShellTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-beans", "spring-boot", "spring-context","spring-web", "spring-webmvc","spring-boot-autoconfigure")
            .build()

    override val recipe: Recipe
        get() = Spring4Shell()

    @Test
    fun spring4Shell() = assertChanged(
        before = """
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            class Test {
                @Bean
                String existingBean() {
                    return "hi";
                }
            }
        """,
        after = """
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
            import org.springframework.context.annotation.Bean;
            import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
            
            @SpringBootApplication
            class Test {
                @Bean
                String existingBean() {
                    return "hi";
                }
            
                @Bean
                public WebMvcRegistrations mvcRegistrations() {
                    return new WebMvcRegistrations() {
                        @Override
                        public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
                            return null;
                        }
                    };
                }
            }
        """,
    )
}
