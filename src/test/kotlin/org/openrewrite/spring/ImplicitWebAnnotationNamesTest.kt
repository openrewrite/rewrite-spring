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
import org.openrewrite.java.Java11Parser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaParser.dependenciesFromClasspath

class ImplicitWebAnnotationNamesTest {
    val jp = Java11Parser.builder()
            .classpath(dependenciesFromClasspath("spring-web"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun removeUnnecessaryAnnotationArgument() {
        val controller = jp.parse("""
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

    @Issue("#4")
    @Test
    fun removeUnnecessarySpacingInFollowingAnnotationArgument() {
        val controller = jp.parse("""
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @GetMapping("/{id}")
                public ResponseEntity<String> getUser(
                    @RequestParam(name = "count", defaultValue = 3) int count) {
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
                public ResponseEntity<String> getUser(
                    @RequestParam(defaultValue = 3) int count) {
                }
            }
        """)
    }

    @Test
    fun dontRemoveModelAttributeOnMethods() {
        val controller = jp.parse("""
            import org.springframework.web.bind.annotation.*;
            import java.util.*;
            
            public class UsersController {
                @ModelAttribute("types")
                public Collection<String> populateUserTypes() {
                    return Arrays.asList("free", "premium");
                }
            }
        """.trimIndent())

        val fixed = controller.refactor().visit(ImplicitWebAnnotationNames()).fix().fixed

        assertRefactored(fixed, """
            import org.springframework.web.bind.annotation.*;
            import java.util.*;
            
            public class UsersController {
                @ModelAttribute("types")
                public Collection<String> populateUserTypes() {
                    return Arrays.asList("free", "premium");
                }
            }
        """)
    }
}
