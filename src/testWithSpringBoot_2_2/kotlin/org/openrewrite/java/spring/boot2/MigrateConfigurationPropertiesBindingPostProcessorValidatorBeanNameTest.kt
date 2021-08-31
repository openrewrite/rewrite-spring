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
package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateConfigurationPropertiesBindingPostProcessorValidatorBeanNameTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-boot", "spring-beans", "spring-core", "spring-context")
            .build()

    override val recipe: Recipe
        get() = MigrateConfigurationPropertiesBindingPostProcessorValidatorBeanName()

    @Test
    fun updateFieldAccess() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;

            class Test {
                static void method() {
                    String value = ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.EnableConfigurationProperties;

            class Test {
                static void method() {
                    String value = EnableConfigurationProperties.VALIDATOR_BEAN_NAME;
                }
            }
        """
    )

    @Test
    fun updateStaticConstant() = assertChanged(
        before = """
            import static org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;

            class Test {
                static void method() {
                    String value = VALIDATOR_BEAN_NAME;
                }
            }
        """,
        after = """
            import static org.springframework.boot.context.properties.EnableConfigurationProperties.VALIDATOR_BEAN_NAME;

            class Test {
                static void method() {
                    String value = VALIDATOR_BEAN_NAME;
                }
            }
        """
    )

    @Test
    fun updateFullyQualifiedTarget() = assertChanged(
        before = """
            class Test {
                static void method() {
                    String value = org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.VALIDATOR_BEAN_NAME;
                }
            }
        """,
        after = """
            class Test {
                static void method() {
                    String value = org.springframework.boot.context.properties.EnableConfigurationProperties.VALIDATOR_BEAN_NAME;
                }
            }
        """
    )

}
