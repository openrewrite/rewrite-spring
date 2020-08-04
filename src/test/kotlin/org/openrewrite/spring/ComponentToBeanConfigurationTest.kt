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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import java.nio.file.Path

class ComponentToBeanConfigurationTest(
        override val parser: JavaParser = JavaParser.fromJavaVersion()
                .classpath("spring-boot-autoconfigure", "spring-beans", "spring-context")
                .build(),
        override val visitors: Iterable<RefactorVisitor<*>> = listOf(
                ComponentToBeanConfiguration().apply {
                    setConfigurationClass("MyConfiguration")
                }
        )
) : RefactorVisitorTestForParser<J.CompilationUnit> {

    private val app = "io/moderne/app/MyApplication" to """
        package io.moderne.app;
        
        import org.springframework.boot.autoconfigure.SpringBootApplication;
        
        @SpringBootApplication
        public class MyApplication {
        }
    """.trimIndent()

    private val component = "io/moderne/app/components/MyComponent" to """
        package io.moderne.app.components;
        
        import org.springframework.stereotype.Component;
        
        @Component
        public class MyComponent {
        }
    """.trimIndent()

    private val repository = "io/moderne/app/repositories/MyRepository" to """
        package io.moderne.app.repositories;
        
        import org.springframework.stereotype.Repository;
        
        @Repository
        public class MyRepository {
        }
    """.trimIndent()

    private val service = "io/moderne/app/services/MyService" to """
        package io.moderne.app.services;
        
        import io.moderne.app.repositories.MyRepository;
        import org.springframework.stereotype.Service;
        
        @Service
        public class MyService {
            private final MyRepository repo;
        
            public MyService(MyRepository repo) {
                this.repo = repo;
            }
        }
    """.trimIndent()

//    @Test
//    fun beanWithNoCollaborators(@TempDir tempDir: Path) {
//        parse(tempDir, app, component).map { cu ->
//            assertThat(cu.refactor().visit(visitor).fix().fixed.printTrimmed()).doesNotContain("@Component")
//        }
//
//        val generated = visitor.generated
//
//        assertThat(generated).isNotNull()
//
//        assertRefactored(generated!!, """
//            package io.moderne.app;
//
//            import io.moderne.app.components.MyComponent;
//            import org.springframework.context.annotation.Configuration;
//
//            @Configuration
//            public class MyConfiguration {
//
//                @Bean
//                MyComponent myComponent() {
//                    return new MyComponent();
//                }
//            }
//        """)
//    }

//    @Test
//    fun beanWithConstructorInjectableCollaborators(@TempDir tempDir: Path) {
//        parse(tempDir, app, repository, service).map { cu ->
//            assertThat(cu.refactor().visit(visitor).fix().fixed.printTrimmed()).doesNotContain("@Component")
//        }
//
//        val generated = visitor.generated
//
//        assertThat(generated).isNotNull()
//
//        assertRefactored(generated!!, """
//            package io.moderne.app;
//
//            import io.moderne.app.repositories.MyRepository;
//            import io.moderne.app.services.MyService;
//            import org.springframework.context.annotation.Configuration;
//
//            @Configuration
//            public class MyConfiguration {
//
//                @Bean
//                MyRepository myRepository() {
//                    return new MyRepository();
//                }
//
//                @Bean
//                MyService myService(MyRepository repo) {
//                    return new MyService(repo);
//                }
//            }
//        """)
//    }
//
//    @Test
//    fun beanWithFieldInjectedCollaborators(@TempDir tempDir: Path) {
//        val serviceFieldInjectable = "io/moderne/app/services/MyService" to """
//            package io.moderne.app.services;
//
//            import io.moderne.app.repositories.MyRepository;
//            import org.springframework.beans.factory.annotation.Autowired;
//            import org.springframework.stereotype.Service;
//
//            @Service
//            public class MyService {
//                @Autowired
//                private MyRepository repo;
//
//                public void setRepo(MyRepository repo) {
//                    this.repo = repo;
//                }
//            }
//        """.trimIndent()
//
//        parse(tempDir, app, repository, serviceFieldInjectable).map { cu ->
//            assertThat(cu.refactor().visit(visitor).fix().fixed.printTrimmed()).doesNotContain("@Component")
//        }
//
//        val generated = visitor.generated
//
//        assertThat(generated).isNotNull()
//
//        assertRefactored(generated!!, """
//            package io.moderne.app;
//
//            import io.moderne.app.repositories.MyRepository;
//            import io.moderne.app.services.MyService;
//            import org.springframework.context.annotation.Configuration;
//
//            @Configuration
//            public class MyConfiguration {
//
//                @Bean
//                MyRepository myRepository() {
//                    return new MyRepository();
//                }
//
//                @Bean
//                MyService myService(MyRepository repo) {
//                    MyService myService = new MyService();
//                    myService.setRepo(repo);
//                    return myService;
//                }
//            }
//        """)
//    }

    private fun parse(root: Path, vararg sources: Pair<String, String>): List<J.CompilationUnit> = parser.parse(
            sources.map { s ->
                val sourcePath = root.resolve(Path.of(s.first + ".java"))
                sourcePath.parent.toFile().mkdirs()
                sourcePath.toFile().writeText(s.second)
                sourcePath
            },
            null
    )
}
