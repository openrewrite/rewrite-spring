package org.gradle.rewrite.spring

import com.netflix.rewrite.Parser
import com.netflix.rewrite.Parser.dependenciesFromClasspath
import org.junit.jupiter.api.Test

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
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ConstructorInjection()).fix().fixed

        assertRefactored(fixed, """
            public class UsersController {
                private final UsersService usersService;
            
                private final UsernameService usernameService;
            
                public UsersController(UsersService usersService, UsernameService usernameService) {
                }
            }
        """.trimIndent())
    }
}
