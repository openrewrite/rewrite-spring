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
import org.openrewrite.Recipe
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.JavaParser
import org.openrewrite.java.spring.boot2.SpringRunnerToSpringExtension

class SpringRunnerToSpringExtensionTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit", "spring-test")
            .build()

    override val recipe: Recipe
        get() = SpringRunnerToSpringExtension()

    @Test
    fun springRunnerToExtension() = assertChanged(
            before = """
                import org.junit.runner.RunWith;
                import org.springframework.test.context.junit4.SpringRunner;

                @RunWith(SpringRunner.class)
                class A {}
            """,
            after = """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.springframework.test.context.junit.jupiter.SpringExtension;
                
                @ExtendWith(SpringExtension.class)
                class A {}
            """
    )

    @Test
    fun springJUnit4ClassRunnerRunnerToExtension() = assertChanged(
            before = """
                import org.junit.runner.RunWith;
                import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

                @RunWith(SpringJUnit4ClassRunner.class)
                class A {}
            """,
            after = """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.springframework.test.context.junit.jupiter.SpringExtension;
                
                @ExtendWith(SpringExtension.class)
                class A {}
            """
    )


    @Test
    fun leavesOtherRunnersAlone() = assertUnchanged(
            before = """
                package a;
                
                import org.junit.runner.RunWith;
                
                @RunWith(B.class)
                class A {}
            """,
            dependsOn = arrayOf(
                    """
                        package a;
                        
                        class B {}
                    """
            )
    )
}
