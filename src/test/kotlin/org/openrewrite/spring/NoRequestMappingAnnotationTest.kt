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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.java.Java11Parser
import org.openrewrite.java.JavaParser

class NoRequestMappingAnnotationTest {
    val jp = Java11Parser.builder()
            .classpath(JavaParser.dependenciesFromClasspath("spring-web"))
            .build()

    @BeforeEach
    fun beforeEach() {
        jp.reset()
    }

    @Test
    fun requestMapping() {
        val controller = jp.parse("""
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
        """.trimIndent())

        val fixed = controller.refactor().visit(NoRequestMappingAnnotation()).fix().fixed

        assertRefactored(fixed, """
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
        """)
    }

    @Issue("#3")
    @Test
    fun requestMappingWithMultipleMethods() {
        val controller = jp.parse("""
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
        """.trimIndent())

        val fix = controller.refactor().visit(NoRequestMappingAnnotation()).fix()

        assertRefactored(fix.fixed, """
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
        """)

        assertThat(fix.rulesThatMadeChanges).isEmpty()
    }
}
