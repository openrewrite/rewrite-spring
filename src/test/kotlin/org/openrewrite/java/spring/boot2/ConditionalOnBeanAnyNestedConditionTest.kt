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
import org.openrewrite.RecipeTest
import org.openrewrite.java.JavaParser

class ConditionalOnBeanAnyNestedConditionTest : RecipeTest {

    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot-autoconfigure", "spring-boot", "spring-web")
            .build()
    override val recipe: Recipe
        get() = ConditionalOnBeanAnyNestedCondition()

    @Test
    fun addParentClassExtendsAnotherNoChange() = assertUnchanged(
        before = """
                import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                
                class ThisOrThatCondition extends Object {
                
                    @ConditionalOnBean(This.class)
                    static class ThisCondition {
                    }
        
                    @ConditionalOnBean(That.class)
                    static class ThatCondition {
                    }
                }
            """
    )

    @Test
    fun addAnyNestedCondition() = assertChanged(
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

    @Test
    fun addAnyNestedConditionWithExistingConstructor() = assertChanged(
        before = """
                import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                
                class ThisOrThatCondition {
                    private final String someString;
                    
                    public ThisOrThatCondition() {
                        someString = "x";
                    }
                
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
                
                class ThisOrThatCondition extends AnyNestedCondition {
                    private final String someString;
                    
                    public ThisOrThatCondition() {
                        super(ConfigurationPhase.REGISTER_BEAN);
                        someString = "x";
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

    @Test
    fun addAnyNestedConditionWithExistingEmptyConstructor() = assertChanged(
        before = """
                import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                
                class ThisOrThatCondition {
                    public ThisOrThatCondition() {}
                
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
