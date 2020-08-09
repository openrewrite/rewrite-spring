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

class ConstructorInjectionTest : RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf()

    @Test
    fun constructorInjection() = assertRefactored(
            visitors = listOf(ConstructorInjection()),
            before = """
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
            """,
            after = """
                public class UsersController {
                    private final UsersService usersService;
                
                    private final UsernameService usernameService;
                
                    public UsersController(UsersService usersService, UsernameService usernameService) {
                        this.usersService = usersService;
                        this.usernameService = usernameService;
                    }
                }
            """
    )

    @Test
    fun constructorInjectionWithLombok() = assertRefactored(
            visitors = listOf(ConstructorInjection().apply { setUseLombokRequiredArgsAnnotation(true) }),
            before = """
                import org.springframework.beans.factory.annotation.Autowired;
                
                public class UsersController {
                    @Autowired
                    private UsersService usersService;
                
                    @Autowired
                    UsernameService usernameService;
                }
            """,
            after = """
                import lombok.RequiredArgsConstructor;
            
                @RequiredArgsConstructor
                public class UsersController {
                    private final UsersService usersService;
                
                    private final UsernameService usernameService;
                }
            """
    )

    @Test
    fun constructorInjectionWithJSR305() = assertRefactored(
            visitors = listOf(ConstructorInjection().apply { setUseJsr305Annotations(true) }),
            before = """
                import org.springframework.beans.factory.annotation.Autowired;

                public class UsersController {
                    @Autowired(required = false)
                    private UsersService usersService;
                
                    @Autowired
                    UsernameService usernameService;
                }
            """,
            after = """
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
            """
    )
}
