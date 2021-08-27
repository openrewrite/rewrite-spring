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

class MigrateMultipartConfigFactoryTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-core")
            .build()

    override val recipe: Recipe
        get() = MigrateMultipartConfigFactory()

    @Test
    fun doNotChangeCurrentApi() = assertUnchanged(
        before = """
            import org.springframework.boot.web.servlet.MultipartConfigFactory;
            import org.springframework.util.unit.DataSize;
            
            class Test {
                void method() {
                    MultipartConfigFactory factory = new MultipartConfigFactory();
                    factory.setLocation(null);
                }
            }
        """
    )

    @Test
    fun changeDeprecatedMethods() = assertChanged(
        before = """
            import org.springframework.boot.web.servlet.MultipartConfigFactory;
            import org.springframework.util.unit.DataSize;
            
            class Test {
                void method() {
                    MultipartConfigFactory factory = new MultipartConfigFactory();
                    factory.setMaxFileSize(1);
                    factory.setMaxFileSize("1");
                    factory.setMaxRequestSize(1);
                    factory.setMaxRequestSize("1");
                    factory.setFileSizeThreshold(1);
                    factory.setFileSizeThreshold("1");
                }
            }
        """,
        after = """
            import org.springframework.boot.web.servlet.MultipartConfigFactory;
            import org.springframework.util.unit.DataSize;
            
            class Test {
                void method() {
                    MultipartConfigFactory factory = new MultipartConfigFactory();
                    factory.setMaxFileSize(DataSize.ofBytes(1));
                    factory.setMaxFileSize(DataSize.parse("1"));
                    factory.setMaxRequestSize(DataSize.ofBytes(1));
                    factory.setMaxRequestSize(DataSize.parse("1"));
                    factory.setFileSizeThreshold(DataSize.ofBytes(1));
                    factory.setFileSizeThreshold(DataSize.parse("1"));
                }
            }
        """
    )
}
