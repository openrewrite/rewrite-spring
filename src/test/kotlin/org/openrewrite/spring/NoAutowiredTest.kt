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

import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class NoAutowiredTest : RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(NoAutowired())

    @Test
    fun removeAutowiredAnnotations() = assertRefactored(
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
                import org.springframework.beans.factory.annotation.Autowired;
                
                public class DatabaseConfiguration { 
                    private final DataSource dataSource;
                
                    public DatabaseConfiguration(DataSource dataSource) {
                    }
                }
            """
    )
}
