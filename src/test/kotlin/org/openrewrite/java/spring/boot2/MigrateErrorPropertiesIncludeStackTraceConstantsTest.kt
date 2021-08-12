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
package org.openrewrite.java.spring.org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.boot2.MigrateErrorPropertiesIncludeStackTraceConstants

class MigrateErrorPropertiesIncludeStackTraceConstantsTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion().classpath("java").build()

    override val recipe =
        MigrateErrorPropertiesIncludeStackTraceConstants()

    // Explicit pseudo files, since the files exist in spring 2.3.x and 1.4.x is loaded on the classpath.
    companion object {
        const val source = """
            package org.springframework.boot.autoconfigure.web;

            public class ErrorProperties {
                public enum IncludeStacktrace {
                    NEVER, ALWAYS, ON_PARAM, ON_TRACE_PARAM
                }
            }
        """
    }

    @Test
    fun doNotUpdateFieldsInTargetClass() = assertUnchanged(
        before = source
    )

    @Test
    fun doNotUpdateCurrentAPIs() = assertUnchanged(
        dependsOn = arrayOf(source),
        before = """
            package org.test;

            import org.springframework.boot.autoconfigure.web.ErrorProperties;

            import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ALWAYS;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ErrorProperties.IncludeStacktrace.NEVER;
                }
                void methodB() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ALWAYS;
                }
            }
        """
    )

    @Test
    fun updateFieldAccessToRecommendedReplacements() = assertChanged(
        dependsOn = arrayOf(source),
        before = """
            package org.test;

            import org.springframework.boot.autoconfigure.web.ErrorProperties;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ErrorProperties.IncludeStacktrace.ON_TRACE_PARAM;
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.autoconfigure.web.ErrorProperties;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ErrorProperties.IncludeStacktrace.ON_PARAM;
                }
            }
        """
    )

    @Test
    fun updateEnumAccessToRecommendedReplacements() = assertChanged(
        dependsOn = arrayOf(source),
        before = """
            package org.test;

            import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = IncludeStacktrace.ON_TRACE_PARAM;
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = IncludeStacktrace.ON_PARAM;
                }
            }
        """
    )

    @Test
    fun updateStaticAccessToRecommendedReplacements() = assertChanged(
        dependsOn = arrayOf(source),
        before = """
            package org.test;

            import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ON_TRACE_PARAM;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ON_TRACE_PARAM;
                }
            }
        """,
        after = """
            package org.test;

            import static org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeStacktrace.ON_PARAM;
            
            class Test {
                void methodA() {
                    ErrorProperties.IncludeStacktrace doNotUpdate = ON_PARAM;
                }
            }
        """
    )
}
