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
package org.openrewrite.java.spring.org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Parser
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.boot2.UnnecessarySpringExtension

class UnnecessarySpringExtensionTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot-test", "junit-jupiter-api")
            .dependsOn(
                listOf(
                    Parser.Input.fromString(
                        """
                            package org.springframework.test.context.junit.jupiter;
                            public class SpringExtension {}
                        """.trimIndent()
                    )
                )
            )
            .build()

    override val recipe: Recipe
        get() = UnnecessarySpringExtension()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/43")
    @Test
    fun removeSpringExtensionIfSpringBootTestIsPresent() = assertChanged(
        before = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.test.context.junit.jupiter.SpringExtension;
            
            @SpringBootTest
            @ExtendWith(SpringExtension.class)
            class Test {
            }
        """,
        after = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.springframework.boot.test.context.SpringBootTest;
            
            @SpringBootTest
            class Test {
            }
        """
    )
}
