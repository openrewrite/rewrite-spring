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
import org.openrewrite.Issue
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateLoggingSystemPropertyConstantsTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot")
            .build()

    override val recipe = MigrateLoggingSystemPropertyConstants()

    @Test
    fun doNotUpdateCurrentAPIs() = assertUnchanged(
        before = """
            package org.test;

            import org.springframework.boot.logging.LoggingSystemProperties;

            import static org.springframework.boot.logging.LoggingSystemProperties.CONSOLE_LOG_PATTERN;
            
            class Test {
                void method() {
                    String valueA = LoggingSystemProperties.EXCEPTION_CONVERSION_WORD;
                    String valueB = CONSOLE_LOG_PATTERN;
                }
            }
        """
    )

    @Test
    fun updateFieldAccess() = assertChanged(
        before = """
            package org.test;

            import org.springframework.boot.logging.LoggingSystemProperties;
            
            class Test {
                void method() {
                    String valueA = LoggingSystemProperties.FILE_CLEAN_HISTORY_ON_START;
                    String valueB = LoggingSystemProperties.FILE_MAX_HISTORY;
                    String valueC = LoggingSystemProperties.FILE_MAX_SIZE;
                    String valueD = LoggingSystemProperties.FILE_TOTAL_SIZE_CAP;
                    String valueE = LoggingSystemProperties.ROLLING_FILE_NAME_PATTERN;
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.logging.logback.LogbackLoggingSystemProperties;
            
            class Test {
                void method() {
                    String valueA = LogbackLoggingSystemProperties.ROLLINGPOLICY_CLEAN_HISTORY_ON_START;
                    String valueB = LogbackLoggingSystemProperties.ROLLINGPOLICY_MAX_HISTORY;
                    String valueC = LogbackLoggingSystemProperties.ROLLINGPOLICY_MAX_FILE_SIZE;
                    String valueD = LogbackLoggingSystemProperties.ROLLINGPOLICY_TOTAL_SIZE_CAP;
                    String valueE = LogbackLoggingSystemProperties.ROLLINGPOLICY_FILE_NAME_PATTERN;
                }
            }
        """
    )

    @Test
    fun updateStaticConstant() = assertChanged(
        before = """
            package org.test;

            import static org.springframework.boot.logging.LoggingSystemProperties.*;
            
            class Test {
                void method() {
                    String valueA = FILE_CLEAN_HISTORY_ON_START;
                    String valueB = FILE_MAX_HISTORY;
                    String valueC = FILE_MAX_SIZE;
                    String valueD = FILE_TOTAL_SIZE_CAP;
                    String valueE = ROLLING_FILE_NAME_PATTERN;
                }
            }
        """,
        after = """
            package org.test;

            import static org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.*;
            
            class Test {
                void method() {
                    String valueA = ROLLINGPOLICY_CLEAN_HISTORY_ON_START;
                    String valueB = ROLLINGPOLICY_MAX_HISTORY;
                    String valueC = ROLLINGPOLICY_MAX_FILE_SIZE;
                    String valueD = ROLLINGPOLICY_TOTAL_SIZE_CAP;
                    String valueE = ROLLINGPOLICY_FILE_NAME_PATTERN;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/89")
    @Test
    fun updateFullyQualifiedTarget() = assertChanged(
        before = """
            package org.test;
            
            class Test {
                void method() {
                    String valueA = org.springframework.boot.logging.LoggingSystemProperties.FILE_CLEAN_HISTORY_ON_START;
                    String valueB = org.springframework.boot.logging.LoggingSystemProperties.FILE_MAX_HISTORY;
                    String valueC = org.springframework.boot.logging.LoggingSystemProperties.FILE_MAX_SIZE;
                    String valueD = org.springframework.boot.logging.LoggingSystemProperties.FILE_TOTAL_SIZE_CAP;
                    String valueE = org.springframework.boot.logging.LoggingSystemProperties.ROLLING_FILE_NAME_PATTERN;
                }
            }
        """,
        after = """
            package org.test;
            
            class Test {
                void method() {
                    String valueA = org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.ROLLINGPOLICY_CLEAN_HISTORY_ON_START;
                    String valueB = org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.ROLLINGPOLICY_MAX_HISTORY;
                    String valueC = org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.ROLLINGPOLICY_MAX_FILE_SIZE;
                    String valueD = org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.ROLLINGPOLICY_TOTAL_SIZE_CAP;
                    String valueE = org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.ROLLINGPOLICY_FILE_NAME_PATTERN;
                }
            }
        """
    )
}
