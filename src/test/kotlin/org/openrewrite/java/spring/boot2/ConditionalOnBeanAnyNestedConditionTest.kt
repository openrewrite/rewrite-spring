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

import org.openrewrite.java.spring.boot2.ConditionalOnBeanAnyNestedCondition
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class ConditionalOnBeanAnyNestedConditionTest : RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-boot-autoconfigure", "spring-boot", "spring-web")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> =
            listOf(ConditionalOnBeanAnyNestedCondition())

    @Test
    fun addAnyNestedCondition() = assertRefactored(
            dependencies = listOf("""
                class This {}
                class That {}
            """),
            before = """
                import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                
                class ThisOrThatCondition {
                
                    @ConditionalOnBean(This.class)
                    static class ThisCondition {
                    }
        
                    @ConditionalOnBean(That.class)
                    static class ThatCondition {
                    }
                }
            """,
            after = """
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
            """
    )
}
