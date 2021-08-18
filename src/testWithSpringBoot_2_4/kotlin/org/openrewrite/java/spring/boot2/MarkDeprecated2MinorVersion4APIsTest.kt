/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MarkDeprecatedSpringBoot2MinorVersion4APIsTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-context", "spring-core")
            .logCompilationWarningsAndErrors(true)
            .build()

    override val recipe: Recipe
        get() = Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.minorversion4.SpringApplicationBuilderContextClass")

    @Test
    fun markDeprecatedMethodTest() = assertChanged(
        before = """
            package org.test;

            import org.springframework.boot.builder.SpringApplicationBuilder;
            import org.springframework.context.support.GenericApplicationContext;

            public class Test {
                public void method() {
                    SpringApplicationBuilder builder = new SpringApplicationBuilder(String.class);
                    builder = builder.contextClass(GenericApplicationContext.class);
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.builder.SpringApplicationBuilder;
            import org.springframework.context.support.GenericApplicationContext;
            
            public class Test {
                public void method() {
                    SpringApplicationBuilder builder = new SpringApplicationBuilder(String.class);
                    builder = /*~~> Source of classMethod deprecation: Spring Boot 2. Reason: Deprecated since 2.4.0 for removal in 2.6.0 in favor of SpringApplicationBuilder.contextFactory(ApplicationContextFactory).. Link: https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/builder/SpringApplicationBuilder.html#contextClass-java.lang.Class-. ~~*/builder.contextClass(GenericApplicationContext.class);
                }
            }
        """
    )

}
