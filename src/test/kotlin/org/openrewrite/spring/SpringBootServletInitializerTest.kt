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
package org.openrewrite.spring

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorPlan
import org.openrewrite.java.Java11Parser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class SpringBootServletInitializerTest {
    val jp = Java11Parser.builder()
            .classpath(JavaParser.dependenciesFromClasspath("spring-boot"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun changeType() {
        val a = jp.parse("""
            import org.springframework.boot.web.support.SpringBootServletInitializer;
            
            public class MyApplication extends SpringBootServletInitializer {
            }
        """.trimIndent())

        val plan = RefactorPlan.builder()
                .scanResources()
                .build()

        val fixed = a.refactor()
                .visit(plan.visitors(J::class.java, "spring"))
                .fix().fixed

        assertRefactored(fixed, """
            import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
            
            public class MyApplication extends SpringBootServletInitializer {
            }
        """)
    }
}
