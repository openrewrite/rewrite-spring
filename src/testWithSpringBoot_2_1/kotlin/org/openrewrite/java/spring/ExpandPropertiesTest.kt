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
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.Issue
import org.openrewrite.Recipe
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

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/135")
    @Test
    fun `Duplicate properties should be coalesced`(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText("""
                management: test
                spring:
                  mail:
                    protocol: smtp
                    properties.mail.smtp.connection-timeout: 1000
                    properties.mail.smtp.timeout: 2000
                    properties.mail.smtp.write-timeout: 3000
            """.trimIndent())},
        after = """
            management: test
            spring:
              mail:
                protocol: smtp
                properties:
                  mail:
                    smtp:
                      connection-timeout: 1000
                      timeout: 2000
                      write-timeout: 3000
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/161")
    @Test
    fun `Duplicate property has dot syntax`(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText("""
                server.context-path: /context
                server:
                  port: 8888
                customize:
                  group1: value1
            """.trimIndent())},
            after = """
                server:
                  context-path: /context
                  port: 8888
                customize:
                  group1: value1
            """
    )
}
