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

class NoRequestMappingAnnotationTest : RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-web")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(NoRequestMappingAnnotation())

    @Test
    fun requestMapping() = assertRefactored(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = HEAD)
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                
                    @RequestMapping(method = GET)
                    public ResponseEntity<List<String>> getUsers() {
                        return null;
                    }
    
                    @RequestMapping(path = "/{id}", method = RequestMethod.GET)
                    public ResponseEntity<String> getUser(@PathVariable("id") Long id) {
                        return null;
                    }
                    
                    @RequestMapping
                    public ResponseEntity<List<String>> getUsersNoRequestMethod() {
                        return null;
                    }
                }
            """,
            after = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = HEAD)
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                
                    @GetMapping
                    public ResponseEntity<List<String>> getUsers() {
                        return null;
                    }
    
                    @GetMapping("/{id}")
                    public ResponseEntity<String> getUser(@PathVariable("id") Long id) {
                        return null;
                    }
                    
                    @GetMapping
                    public ResponseEntity<List<String>> getUsersNoRequestMethod() {
                        return null;
                    }
                }
            """
    )

    @Test
    fun postMapping() = assertRefactored(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.POST;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = POST)
                    public ResponseEntity<List<String>> getUsersPost() {
                        return null;
                    }
                }
            """,
            after = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @PostMapping
                    public ResponseEntity<List<String>> getUsersPost() {
                        return null;
                    }
                }
            """
    )

    @Test
    fun hasValueParameter() = assertRefactored(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(value = "/user/{userId}/edit", method = RequestMethod.POST)
                    public ResponseEntity<List<String>> getUsersPost(String userId) {
                        return null;
                    }
                }
            """,
            after = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @PostMapping("/user/{userId}/edit")
                    public ResponseEntity<List<String>> getUsersPost(String userId) {
                        return null;
                    }
                }
            """
    )

    @Issue("#3")
    @Test
    fun requestMappingWithMultipleMethods() = assertUnchanged(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = { HEAD, GET })
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                }
            """
    )
}
