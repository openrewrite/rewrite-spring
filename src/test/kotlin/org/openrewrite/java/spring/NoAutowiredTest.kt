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
package org.openrewrite.java.spring

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class NoAutowiredTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()

    override val recipe: Recipe
        get() = Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.NoAutowired")

    @Test
    fun removeAutowiredAnnotations() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                @Autowired
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
            
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Test
    fun optionalAutowiredAnnotations() = assertUnchanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                public DatabaseConfiguration(@Autowired(required = false) DataSource dataSource) {
                }
            }
        """
    )

    @Test
    fun noAutowiredAnnotations() = assertUnchanged(
        before = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                @Primary
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )
}
