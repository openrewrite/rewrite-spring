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
package org.openrewrite.java.spring.framework

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("BooleanMethodNameMustStartWithQuestion")
class EnvironmentAcceptsProfilesTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-core")
            .build()

    override val recipe: Recipe
        get() = EnvironmentAcceptsProfiles()

    @Test
    fun acceptsSingleProfile() = assertChanged(
        before = """
            import org.springframework.core.env.Environment;

            class Test {
                static boolean method(Environment env) {
                    return env.acceptsProfiles("production");
                }
            }
        """,
        after = """
            import org.springframework.core.env.Environment;
            import org.springframework.core.env.Profiles;

            class Test {
                static boolean method(Environment env) {
                    return env.acceptsProfiles(Profiles.of("production"));
                }
            }
        """
    )

    @Test
    fun acceptsMultipleProfiles() = assertChanged(
        before = """
            import org.springframework.core.env.Environment;

            class Test {
                static boolean method(Environment env) {
                    return env.acceptsProfiles("test", "integration");
                }
            }
        """,
        after = """
            import org.springframework.core.env.Environment;
            import org.springframework.core.env.Profiles;

            class Test {
                static boolean method(Environment env) {
                    return env.acceptsProfiles(Profiles.of("test", "integration"));
                }
            }
        """
    )

}