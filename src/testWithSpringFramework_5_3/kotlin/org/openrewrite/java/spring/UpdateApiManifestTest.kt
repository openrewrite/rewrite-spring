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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import java.nio.file.Paths

class UpdateApiManifestTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-web")
            .build()

    override val recipe: Recipe
        get() = UpdateApiManifest()

    @Test
    fun requestMappingWithMethod() {
        val recipeRun = recipe.run(parser.parse("""
            import java.util.List;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/users")
            public class UsersController {
                @RequestMapping(value = "/post", method = RequestMethod.POST)
                public ResponseEntity<List<String>> getUsersPost() {
                    return null;
                }
            }
        """.trimIndent()))

        assertThat(recipeRun.results.size).isEqualTo(1)
        assertThat(recipeRun.results[0].after!!.sourcePath).isEqualTo(Paths.get("META-INF/api-manifest.txt"))
        assertThat(recipeRun.results[0].after!!.printAll()).isEqualTo("POST /users/post")
    }
}
