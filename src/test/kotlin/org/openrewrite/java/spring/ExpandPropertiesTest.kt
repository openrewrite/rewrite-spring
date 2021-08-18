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
package org.openrewrite.java.spring.org.openrewrite.java.spring

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.Recipe
import org.openrewrite.java.spring.ExpandProperties
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class ExpandPropertiesTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = ExpandProperties()

    @Test
    fun expandProperties(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText("""
                management: test
                spring.application:
                  name: main
                  description: a description
            """.trimIndent())
        },
        after = """
            management: test
            spring:
              application:
                name: main
                description: a description
        """
    )
}
