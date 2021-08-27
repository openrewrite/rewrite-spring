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

class GetErrorAttributesTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-web")
            .build()

    override val recipe: Recipe
        get() = GetErrorAttributes()

    @Test
    fun whenLiteralTrueUseDefaultsIncludingStacktraceEnum() = assertChanged(
        before = """
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest) {
                    return this.errorAttributes.getErrorAttributes(webRequest, true);
                }
            }
        """,
        after = """
            import org.springframework.boot.web.error.ErrorAttributeOptions;
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest) {
                    return this.errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE));
                }
            }
        """
    )

    @Test
    fun whenLiteralFalseUseDefaults() = assertChanged(
        before = """
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest) {
                    return this.errorAttributes.getErrorAttributes(webRequest, false);
                }
            }
        """,
        after = """
            import org.springframework.boot.web.error.ErrorAttributeOptions;
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest) {
                    return this.errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
                }
            }
        """
    )

    @Test
    fun whenArgumentIsVariableUseTernary() = assertChanged(
        before = """
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace) {
                    return this.errorAttributes.getErrorAttributes(webRequest, includeStackTrace);
                }
            }
        """,
        after = """
            import org.springframework.boot.web.error.ErrorAttributeOptions;
            import org.springframework.boot.web.servlet.error.ErrorAttributes;
            import org.springframework.web.context.request.WebRequest;

            import java.util.Map;

            class Test {
                private final ErrorAttributes errorAttributes;

                Test(ErrorAttributes errorAttributes) {
                    this.errorAttributes = errorAttributes;
                }

                private Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace) {
                    return this.errorAttributes.getErrorAttributes(webRequest, includeStackTrace ? ErrorAttributeOptions.defaults().including(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults());
                }
            }
        """
    )

}
