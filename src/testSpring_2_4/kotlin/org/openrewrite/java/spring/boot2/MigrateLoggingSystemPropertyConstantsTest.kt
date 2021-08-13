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
package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateLoggingSystemPropertyConstantsTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion().classpath("java").build()

    override val recipe = MigrateLoggingSystemPropertyConstants()

    companion object {
        const val source = """
            package org.springframework.boot.logging.logback;

            public class LoggingSystemProperties {
                public static final String CONSOLE_LOG_CHARSET = "";
                public static final String CONSOLE_LOG_PATTERN = "";
                public static final String FILE_CLEAN_HISTORY_ON_START = "";
                public static final String FILE_MAX_HISTORY = "";
                public static final String FILE_MAX_SIZE = "";
                public static final String FILE_TOTAL_SIZE_CAP = "";
                public static final String ROLLING_FILE_NAME_PATTERN = "";
            }
        """

        const val target = """
            package org.springframework.boot.logging.logback;

            public class LogbackLoggingSystemProperties {
                public static final String ROLLINGPOLICY_CLEAN_HISTORY_ON_START = "";
                public static final String ROLLINGPOLICY_MAX_HISTORY = "";
                public static final String ROLLINGPOLICY_MAX_FILE_SIZE = "";
                public static final String ROLLINGPOLICY_TOTAL_SIZE_CAP = "";
                public static final String ROLLINGPOLICY_FILE_NAME_PATTERN = "";
            }
        """
    }

    @Test
    fun doNotUpdateFieldsInTargetClass() = assertUnchanged(
        before = source
    )

    @Test
    fun doNotUpdateFieldsInNewClass() = assertUnchanged(
        before = target
    )

    @Test
    fun doNotUpdateCurrentAPIs() = assertUnchanged(
        dependsOn = arrayOf(source),
        before = """
            package org.test;

            import org.springframework.boot.logging.logback.LoggingSystemProperties;

            import static org.springframework.boot.logging.logback.LoggingSystemProperties.CONSOLE_LOG_PATTERN;
            
            class Test {
                void methodA() {
                    String doNotUpdate = LoggingSystemProperties.CONSOLE_LOG_CHARSET;
                }
                void methodB() {
                    String doNotUpdate = CONSOLE_LOG_PATTERN;
                }
            }
        """
    )

    @Test
    fun updateToRecommendedReplacements() = assertChanged(
        dependsOn = arrayOf(source, target),
        before = """
            package org.test;

            import org.springframework.boot.logging.logback.LoggingSystemProperties;

            import static org.springframework.boot.logging.logback.LoggingSystemProperties.FILE_MAX_SIZE;
            import static org.springframework.boot.logging.logback.LoggingSystemProperties.FILE_TOTAL_SIZE_CAP;
            import static org.springframework.boot.logging.logback.LoggingSystemProperties.ROLLING_FILE_NAME_PATTERN;
            
            class Test {
                void methodA() {
                    String valueA = LoggingSystemProperties.FILE_CLEAN_HISTORY_ON_START;
                    String valueB = LoggingSystemProperties.FILE_MAX_HISTORY;
                }
                void methodB() {
                    String valueA = FILE_MAX_SIZE;
                    String valueB = FILE_TOTAL_SIZE_CAP;
                    String valueC = ROLLING_FILE_NAME_PATTERN;
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.logging.logback.LogbackLoggingSystemProperties;

            import static org.springframework.boot.logging.logback.LogbackLoggingSystemProperties.*;
            
            class Test {
                void methodA() {
                    String valueA = LogbackLoggingSystemProperties.ROLLINGPOLICY_CLEAN_HISTORY_ON_START;
                    String valueB = LogbackLoggingSystemProperties.ROLLINGPOLICY_MAX_HISTORY;
                }
                void methodB() {
                    String valueA = ROLLINGPOLICY_MAX_FILE_SIZE;
                    String valueB = ROLLINGPOLICY_TOTAL_SIZE_CAP;
                    String valueC = ROLLINGPOLICY_FILE_NAME_PATTERN;
                }
            }
        """
    )
}
