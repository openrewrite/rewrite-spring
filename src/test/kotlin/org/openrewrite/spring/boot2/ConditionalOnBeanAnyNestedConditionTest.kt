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

class ConditionalOnBeanAnyNestedConditionTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("spring-boot-autoconfigure", "spring-boot", "spring-web"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun addAnyNestedCondition() {
        val b = """
            class This {}
            class That {}
        """.trimIndent()

        val a = jp.parse("""
            import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; 
            
            class ThisOrThatCondition {
            
                @ConditionalOnBean(This.class)
                static class ThisCondition {
                }
    
                @ConditionalOnBean(That.class)
                static class ThatCondition {
                }
            }
        """.trimIndent(), b)

        val fixed = a.refactor().visit(ConditionalOnBeanAnyNestedCondition()).fix().fixed

        assertRefactored(fixed, """
            import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
            import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
            import org.springframework.context.annotation.ConfigurationPhase;
            
            class ThisOrThatCondition extends AnyNestedCondition {
            
                public ThisOrThatCondition() {
                    super(ConfigurationPhase.REGISTER_BEAN);
                }
            
                @ConditionalOnBean(This.class)
                static class ThisCondition {
                }

                @ConditionalOnBean(That.class)
                static class ThatCondition {
                }
            }
        """.trimIndent())
    }
}
