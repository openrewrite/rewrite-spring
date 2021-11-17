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
package org.openrewrite.java.spring.org.openrewrite.java.spring

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.Recipe
import org.openrewrite.java.spring.YamlPropertiesToKebabCase
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Path

class YamlPropertiesToKebabCaseTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = YamlPropertiesToKebabCase()

    @Test
    fun singleFlatProperty(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yaml").toFile().apply {
            writeText(//language=yml
                """
              spring.main.showBanner: true
            """.trimIndent()
            )
        },
        after = """
          spring.main.show-banner: true
        """
    )

    @Test
    fun singleNestedProperty(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText(//language=yml
                """
              spring:
                main:
                  showBanner: true
            """.trimIndent()
            )
        },
        after = """
          spring:
            main:
              show-banner: true
        """
    )

    @Test
    fun multipleFlatProperties(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText(//language=yml
                """
              spring.main.showBanner: true
              myCustom.propertyValue.GOES_HERE: example
            """.trimIndent()
            )
        },
        after = """
          spring.main.show-banner: true
          my-custom.property-value.goes-here: example
        """
    )

    @Test
    fun multipleNestedProperties(@TempDir tempDir: Path) = assertChanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText(//language=yml
                """
              SOME.EXAMPLE_CUSTOM:
                firstNested:
                  firstValue: first-example
                  secondValue: second-example
                second_nested: second_nested_example
              another:
                exampleGoes:
                  HERE: example
            """.trimIndent()
            )
        },
        after = """
          some.example-custom:
            first-nested:
              first-value: first-example
              second-value: second-example
            second-nested: second_nested_example
          another:
            example-goes:
              here: example
        """
    )

    @Test
    fun doNotChange(@TempDir tempDir: Path) = assertUnchanged(
        before = tempDir.resolve("application.yml").toFile().apply {
            writeText(//language=yml
                """
              some.example-custom:
                first-nested:
                  first-value: first-example
                  second-value: second-example
                second-nested: second_nested_example
              another:
                example-goes:
                  here: example
            """.trimIndent()
            )
        }
    )

}
