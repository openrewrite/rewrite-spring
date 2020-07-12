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
package org.openrewrite.spring.boot2

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.assertRefactored

class RestTemplateBuilderRequestFactoryTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("spring-boot", "spring-web"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun useSupplierArgument() {
        val a = jp.parse("""
            import org.springframework.boot.web.client.RestTemplateBuilder;
            import org.springframework.http.client.ClientHttpRequestFactory;
            import org.springframework.http.client.SimpleClientHttpRequestFactory;

            public class A {
                {
                    new RestTemplateBuilder()
                        .requestFactory(new SimpleClientHttpRequestFactory());
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(RestTemplateBuilderRequestFactory()).fix().fixed

        assertRefactored(fixed, """
            import org.springframework.boot.web.client.RestTemplateBuilder;
            import org.springframework.http.client.ClientHttpRequestFactory;
            import org.springframework.http.client.SimpleClientHttpRequestFactory;

            public class A {
                {
                    new RestTemplateBuilder()
                        .requestFactory(() -> new SimpleClientHttpRequestFactory());
                }
            }
        """.trimIndent())
    }
}
