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

import okhttp3.ConnectionSpec.Companion.COMPATIBLE_TLS
import okhttp3.ConnectionSpec.Companion.MODERN_TLS
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.xml.XmlParser

class UseSpringBootVersionMavenTest : XmlParser() {
    @Test
    fun latestAvailableVersion() {
        val httpClient = OkHttpClient.Builder()
                .connectionSpecs(listOf(MODERN_TLS, COMPATIBLE_TLS))
                .build()

        assertThat(UseSpringBootVersionMaven.latestMatchingVersion("2.+", httpClient, "https://repo1.maven.org/maven2"))
                .isGreaterThanOrEqualTo("2.3.0.RELEASE")

        assertThat(UseSpringBootVersionMaven.latestMatchingVersion("2.1.+", httpClient, "https://repo1.maven.org/maven2"))
                .isGreaterThanOrEqualTo("2.1.14.RELEASE")
    }

    @Test
    fun upgradeSpringBootVersion() {
        val useLatestSpringBoot = UseSpringBootVersionMaven()
                .apply { setVersion("2.+") }

        val x = parse("""
            <project>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>2.0.0.RELEASE</version>
              </parent>
            </project>
        """.trimIndent())

        val fixed = x.refactor().visit(useLatestSpringBoot).fix().fixed

        assertRefactored(fixed, """
            <project>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>${useLatestSpringBoot.latestMatchingVersion}</version>
              </parent>
            </project>
        """.trimIndent())
    }
}