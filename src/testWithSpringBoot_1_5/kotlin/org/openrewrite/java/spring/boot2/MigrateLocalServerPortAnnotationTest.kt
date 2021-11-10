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
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateLocalServerPortAnnotationTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot")
            .build()

    override val recipe: Recipe
        get() = MigrateLocalServerPortAnnotation()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/116")
    @Test
    fun givenHasStringVariableWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() = assertChanged(
        before = """
            import org.springframework.boot.context.embedded.LocalServerPort;

            public class RandomTestClass {
                @LocalServerPort
                private int port;
            }
        """,
        after = """
            import org.springframework.boot.web.server.LocalServerPort;
            
            public class RandomTestClass {
                @LocalServerPort
                private int port;
            }
        """
    )
}
