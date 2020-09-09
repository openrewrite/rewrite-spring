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
package org.openrewrite.java.spring.internal

import org.assertj.core.api.Assertions.assertThat
import org.openrewrite.java.spring.internal.SpringBootReleases
import org.junit.jupiter.api.Test

class SpringBootReleasesTest {
    @Test
    fun latestAvailableVersion() {
        val releases = SpringBootReleases()

        assertThat(releases.latestMatchingVersion("2.+"))
                .isGreaterThanOrEqualTo("2.3.0.RELEASE")

        assertThat(releases.latestMatchingVersion("2.1.+"))
                .isGreaterThanOrEqualTo("2.1.14.RELEASE")
    }

    @Test
    fun latestPatches() {
        val releases = SpringBootReleases()

        println(releases.latestPatchReleases())
    }
}
