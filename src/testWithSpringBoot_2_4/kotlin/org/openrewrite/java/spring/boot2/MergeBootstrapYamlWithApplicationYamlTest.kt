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
package org.openrewrite.java.spring.boot2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.yaml.YamlParser
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Paths

class MergeBootstrapYamlWithApplicationYamlTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = MergeBootstrapYamlWithApplicationYaml()

    @Test
    fun mergeBootstrap() {
        val applicationYaml = YamlParser().parse(
            """
                spring:
                  application.name: main
                ---
                spring:
                    config:
                        activate:
                            on-profile: test
                name: test
            """.trimIndent()
        ).map { it.withSourcePath(Paths.get("src/main/resources/application.yml")) }

        val bootstrapYaml = YamlParser().parse(
            """
                spring.application:
                  name: not the name
                ---
                spring:
                    config:
                        activate:
                            on-profile: test
                name: test
            """.trimIndent()
        ).map { it.withSourcePath(Paths.get("src/main/resources/bootstrap.yml")) }

        val results = recipe.run(bootstrapYaml + applicationYaml)
            .results.sortedBy { it.before!!.sourcePath.fileName.toString() }

        assertThat(results).hasSize(2)
        assertThat(results[1].after).isNull()
        assertThat(results[0].after!!.printAll()).isEqualTo(
            """
                spring.application.name: main
                ---
                spring.config.activate.on-profile: test
                name: test
            """.trimIndent()
        )
    }
}
