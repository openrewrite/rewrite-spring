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
import org.openrewrite.Issue
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class ImplicitWebAnnotationNamesTest : RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-web")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ImplicitWebAnnotationNames())

    @Test
    fun removeUnnecessaryAnnotationArgument() = assertRefactored(
            before = """
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
            """,
            after = """
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
            """
    )

    @Issue("#4")
    @Test
    fun removeUnnecessarySpacingInFollowingAnnotationArgument() = assertRefactored(
            before = """
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
            """,
            after = """
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
            """
    )

    @Test
    fun dontRemoveModelAttributeOnMethods() = assertUnchanged(
            before = """
                import org.springframework.web.bind.annotation.*;
                import java.util.*;
                
                public class UsersController {
                    @ModelAttribute("types")
                    public Collection<String> populateUserTypes() {
                        return Arrays.asList("free", "premium");
                    }
                }
            """
    )

    @Test
    fun onlyDropCamelCasedNames() = assertRefactored(
            before = """
                import org.springframework.web.bind.annotation.*;
                
                public class UsersController {
                    public ResponseEntity<String> getUser(@PathVariable("id") Long id,
                                                          @PathVariable(value = "another_name") Long anotherName) {
                    }
                }
            """,
            after = """
                import org.springframework.web.bind.annotation.*;
            
                public class UsersController {
                    public ResponseEntity<String> getUser(@PathVariable Long id,
                                                          @PathVariable(value = "another_name") Long anotherName) {
                    }
                }
            """
    )
}
