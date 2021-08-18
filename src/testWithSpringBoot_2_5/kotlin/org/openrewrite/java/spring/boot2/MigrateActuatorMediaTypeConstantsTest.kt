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
import org.openrewrite.Issue
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateActuatorMediaTypeConstantsTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot")
            .logCompilationWarningsAndErrors(true)
            .build()

    override val recipe = MigrateActuatorMediaTypeConstants()

    @Test
    fun updateFieldAccess() = assertChanged(
        before = """
            package org.test;

            import org.springframework.boot.actuate.endpoint.http.ActuatorMediaType;
            
            class Test {
                void method() {
                    String valueA = ActuatorMediaType.V2_JSON;
                    String valueB = ActuatorMediaType.V3_JSON;
                }
            }
        """,
        after = """
            package org.test;

            import org.springframework.boot.actuate.endpoint.ApiVersion;

            class Test {
                void method() {
                    String valueA = ApiVersion.V2;
                    String valueB = ApiVersion.V3;
                }
            }
        """
    )

    @Test
    fun updateStaticConstant() = assertChanged(
        before = """
            package org.test;

            import static org.springframework.boot.actuate.endpoint.http.ActuatorMediaType.V2_JSON;
            import static org.springframework.boot.actuate.endpoint.http.ActuatorMediaType.V3_JSON;
            
            class Test {
                void method() {
                    String valueA = V2_JSON;
                    String valueB = V3_JSON;
                }
            }
        """,
        after = """
            package org.test;

            import static org.springframework.boot.actuate.endpoint.ApiVersion.V2;
            import static org.springframework.boot.actuate.endpoint.ApiVersion.V3;
            
            class Test {
                void method() {
                    String valueA = V2;
                    String valueB = V3;
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
                    String valueA = org.springframework.boot.actuate.endpoint.http.ActuatorMediaType.V2_JSON;
                    String valueB = org.springframework.boot.actuate.endpoint.http.ActuatorMediaType.V3_JSON;
                }
            }
        """,
        after = """
            package org.test;
            
            class Test {
                void method() {
                    String valueA = org.springframework.boot.actuate.endpoint.ApiVersion.V2;
                    String valueB = org.springframework.boot.actuate.endpoint.ApiVersion.V3;
                }
            }
        """
    )
}
