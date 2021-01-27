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
import org.openrewrite.RecipeTest
import org.openrewrite.java.JavaParser

class BeanMethodsNotPublicTest : RecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-context")
            .build()

    override val recipe: Recipe
        get() = BeanMethodsNotPublic()

    @Test
    fun removePublicModifierFromBeanMethods() = assertChanged(
            before = """
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Primary;
                
                public class DatabaseConfiguration { 
                    @Primary
                    @Bean
                    public DataSource dataSource() {
                        return new DataSource();
                    }
                    
                    @Bean
                    public final DataSource dataSource2() {
                        return new DataSource();
                    }
                    
                    @Bean
                    public static final DataSource dataSource3() {
                        return new DataSource();
                    }
                }
            """,
            after = """
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Primary;
                
                public class DatabaseConfiguration { 
                    
                    @Primary
                    @Bean
                    DataSource dataSource() {
                        return new DataSource();
                    }
                    
                    @Bean
                    final DataSource dataSource2() {
                        return new DataSource();
                    }
                    
                    @Bean
                    static final DataSource dataSource3() {
                        return new DataSource();
                    }
                }
            """
    )
}
