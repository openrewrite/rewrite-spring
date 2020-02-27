package org.gradle.rewrite.spring

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.Parser.dependenciesFromClasspath

class ConstructorInjectionTest {
    @Test
    fun constructorInjection() {
        val controller = Parser(dependenciesFromClasspath("spring-beans")).parse("""
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
        val controller = Parser(dependenciesFromClasspath("spring-beans")).parse("""
            import org.springframework.beans.factory.annotation.Autowired;
            public class UsersController {
                @Autowired
                private UsersService usersService;
            
                @Autowired
                UsernameService usernameService;
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ConstructorInjection(true, false)).fix().fixed

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
        val controller = Parser(dependenciesFromClasspath("spring-beans")).parse("""
            import org.springframework.beans.factory.annotation.Autowired;
            public class UsersController {
                @Autowired(required = false)
                private UsersService usersService;
            
                @Autowired
                UsernameService usernameService;
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ConstructorInjection(false, true)).fix().fixed

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
