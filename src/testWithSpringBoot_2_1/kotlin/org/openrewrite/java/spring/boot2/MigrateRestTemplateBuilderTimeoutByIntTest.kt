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

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateRestTemplateBuilderTimeoutByIntTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-web")
            .build()

    override val recipe: Recipe
        get() = MigrateRestTemplateBuilderTimeoutByInt()

    @Test
    fun doNotChangeCurrentApi() = assertUnchanged(
        before = """
            import org.springframework.web.client.RestTemplate;
            import org.springframework.boot.web.client.RestTemplateBuilder;
            import java.time.Duration;
            class Test {
                RestTemplate template = new RestTemplateBuilder()
                    .setConnectTimeout(Duration.ofMillis(1)).build();
            }
        """
    )

    @Test
    fun changeDeprecatedMethods() = assertChanged(
        before = """
            import org.springframework.boot.web.client.RestTemplateBuilder;
            import org.springframework.web.client.RestTemplate;

            class Test {
                RestTemplate template = new RestTemplateBuilder()
                        .setConnectTimeout(1)
                        .setReadTimeout(1)
                        .build();
            }
        """,
        after = """
            import org.springframework.boot.web.client.RestTemplateBuilder;
            import org.springframework.web.client.RestTemplate;
            
            import java.time.Duration;
            
            class Test {
                RestTemplate template = new RestTemplateBuilder()
                        .setConnectTimeout(Duration.ofMillis(1))
                        .setReadTimeout(Duration.ofMillis(1))
                        .build();
            }
        """
    )
}
