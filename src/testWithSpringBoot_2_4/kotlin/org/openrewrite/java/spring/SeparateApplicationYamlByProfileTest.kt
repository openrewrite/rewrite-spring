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
import org.openrewrite.Result
import org.openrewrite.yaml.YamlParser
import org.openrewrite.yaml.YamlRecipeTest
import java.nio.file.Paths

class SeparateApplicationYamlByProfileTest : YamlRecipeTest {
    override val recipe: Recipe
        get() = SeparateApplicationYamlByProfile()

    @Test
    fun separateProfile() {
        val results = recipe.run(YamlParser().parse("""
            name: main
            ---
            spring:
                config:
                    activate:
                        on-profile: test
            name: test
        """.trimIndent()).map { it.withSourcePath(Paths.get("src/main/resources/application.yml")) }).results.groupByProfile()

        assertThat(results["application.yml"]).isEqualTo("name: main")
        assertThat(results["application-test.yml"]).isEqualTo("name: test")
    }

    @Test
    fun onlyHasProfile() {
        val results = recipe.run(YamlParser().parse("""
            spring:
                config:
                    activate:
                        on-profile: test
            name: test
        """.trimIndent()).map { it.withSourcePath(Paths.get("src/main/resources/application.yml")) }).results.groupByProfile()

        assertThat(results["application.yml"]).isEqualTo(null)
        assertThat(results["application-test.yml"]).isEqualTo("name: test")
    }

    @Test
    fun leaveProfileExpressionsAlone() {
        recipe.run(YamlParser().parse("""
            spring:
                config:
                    activate:
                        on-profile: !test
            name: test
        """.trimIndent())).results.isEmpty()
    }

    private fun List<Result>.groupByProfile() = associate { r ->
        (r.after ?: r.before).sourcePath.toFile().name to r.after?.printAll()?.trim()
    }
}
