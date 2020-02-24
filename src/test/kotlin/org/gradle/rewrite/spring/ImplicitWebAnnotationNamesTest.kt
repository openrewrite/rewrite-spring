package org.gradle.rewrite.spring

import com.netflix.rewrite.Parser
import org.junit.jupiter.api.Test

class ImplicitWebAnnotationNamesTest : Parser(dependenciesFromClasspath("spring-web")) {
    @Test
    fun removeUnnecessaryAnnotationArgument() {
        val controller = parse("""
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @GetMapping("/{id}")
                public ResponseEntity<String> getUser(@PathVariable("id") Long id,
                                                      @PathVariable(required = false) Long p2,
                                                      @PathVariable(value = "p3") Long anotherName) {
                }
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ImplicitWebAnnotationNames()).fix().fixed

        assertRefactored(fixed, """
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @GetMapping("/{id}")
                public ResponseEntity<String> getUser(@PathVariable Long id,
                                                      @PathVariable(required = false) Long p2,
                                                      @PathVariable Long p3) {
                }
            }
        """)
    }
}
