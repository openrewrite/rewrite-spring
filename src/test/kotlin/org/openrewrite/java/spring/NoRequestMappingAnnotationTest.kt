/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("SimplifiableAnnotation", "MethodMayBeStatic")
class NoRequestMappingAnnotationTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("spring-web")
                .build()

    override val recipe: Recipe
        get() = NoRequestMappingAnnotation()

    @Test
    fun requestMapping() = assertChanged(
        before = """
            import java.util.*;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.RequestMapping;
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
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
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
        """,
        typeValidation = { identifiers = false }
    )

    @Test
    fun postMapping() = assertChanged(
        before = """
            import java.util.*;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            
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
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            
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
    fun removeUnnecessaryAnnotation() = assertChanged(
        before = """
            import java.util.List;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class UsersController {
                @RequestMapping(value = "/users", method = POST)
                public ResponseEntity<List<String>> getUsersPost() {
                    return null;
                }
            }
        """,
        after = """
            import java.util.List;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class UsersController {
                @PostMapping("/users")
                public ResponseEntity<List<String>> getUsersPost() {
                    return null;
                }
            }
        """
    )

    @Test
    fun hasValueParameter() = assertChanged(
        before = """
            import java.util.*;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.RequestMapping;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @RequestMapping(value = "/user/{userId}/edit", method = {RequestMethod.POST})
                public ResponseEntity<List<String>> getUsersPost(String userId) {
                    return null;
                }
            }
        """,
        after = """
            import java.util.*;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @PostMapping("/user/{userId}/edit")
                public ResponseEntity<List<String>> getUsersPost(String userId) {
                    return null;
                }
            }
        """,
        typeValidation = { identifiers = false }
    )

    @Test
    fun requestMappingWithMultipleMethods() = assertUnchanged(
        before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.RequestMapping;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = {HEAD, GET})
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                }
            """
    )

    @Test
    fun multipleParameters() = assertChanged(
        before = """
            import java.util.List;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMethod;
            import org.springframework.http.MediaType;
            
            @RestController
            public class UsersController {
                @RequestMapping(value = "/user/{userId}/edit", method = RequestMethod.POST, produces = { MediaType.APPLICATION_JSON_VALUE })
                public ResponseEntity<List<String>> getUsersPost(String userId) {
                    return null;
                }
            }
        """,
        after = """
            import java.util.List;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.http.MediaType;
            
            @RestController
            public class UsersController {
                @PostMapping(value = "/user/{userId}/edit", produces = {MediaType.APPLICATION_JSON_VALUE})
                public ResponseEntity<List<String>> getUsersPost(String userId) {
                    return null;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/85")
    @Test
    fun getMappingWithinNestedClass() = assertChanged(
        before = """
            import org.springframework.web.bind.annotation.RequestMapping;
            
            class Test {
                class Inner {
                    @RequestMapping("/api")
                    void test() {
                    }
                }
            }
        """,
        after = """
            import org.springframework.web.bind.annotation.GetMapping;
            
            class Test {
                class Inner {
                    @GetMapping("/api")
                    void test() {
                    }
                }
            }
        """
    )
}
