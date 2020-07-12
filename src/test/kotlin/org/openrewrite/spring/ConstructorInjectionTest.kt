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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaParser.dependenciesFromClasspath

class ConstructorInjectionTest {
    private val jp = JavaParser.fromJavaVersion()
            .classpath(dependenciesFromClasspath("spring-beans"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun constructorInjection() {
        val controller = jp.parse("""
            import org.springframework.beans.factory.annotation.Autowired;
            public class UsersController {
                @Autowired
                private UsersService usersService;
            
                @Autowired
                UsernameService usernameService;
                
                public void setUsersService(UsersService usersService) {
                    this.usersService = usersService;
                }
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ConstructorInjection()).fix().fixed

        assertRefactored(fixed, """
            public class UsersController {
                private final UsersService usersService;
            
                private final UsernameService usernameService;
            
                public UsersController(UsersService usersService, UsernameService usernameService) {
                    this.usersService = usersService;
                    this.usernameService = usernameService;
                }
            }
        """.trimIndent())
    }

    @Test
    fun constructorInjectionWithLombok() {
        val controller = jp.parse("""
            import org.springframework.beans.factory.annotation.Autowired;
            public class UsersController {
                @Autowired
                private UsersService usersService;
            
                @Autowired
                UsernameService usernameService;
            }
        """.trimIndent())

        val fixed = controller.refactor()
                .visit(ConstructorInjection().apply { setUseLombokRequiredArgsAnnotation(true) })
                .fix().fixed

        assertRefactored(fixed, """
            import lombok.RequiredArgsConstructor;
            
            @RequiredArgsConstructor
            public class UsersController {
                private final UsersService usersService;
            
                private final UsernameService usernameService;
            }
        """.trimIndent())
    }

    @Test
    fun constructorInjectionWithJSR305() {
        val controller = jp.parse("""
            import org.springframework.beans.factory.annotation.Autowired;
            public class UsersController {
                @Autowired(required = false)
                private UsersService usersService;
            
                @Autowired
                UsernameService usernameService;
            }
        """.trimIndent())

        val fixed = controller.refactor()
                .visit(ConstructorInjection().apply { setUseJsr305Annotations(true) })
                .fix().fixed

        assertRefactored(fixed, """
            import javax.annotation.Nonnull;
            
            public class UsersController {
                @Nonnull
                private final UsersService usersService;
            
                private final UsernameService usernameService;
            
                public UsersController(UsersService usersService, UsernameService usernameService) {
                    this.usersService = usersService;
                    this.usernameService = usernameService;
                }
            }
        """.trimIndent())
    }
}
