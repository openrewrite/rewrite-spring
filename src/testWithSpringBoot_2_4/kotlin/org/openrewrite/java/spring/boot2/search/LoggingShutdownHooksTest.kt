/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.MavenRecipeTest

/**
 * @author Alex Boyko
 */
class LoggingShutdownHooksTest : MavenRecipeTest {
    override val recipe: Recipe
        get() = LoggingShutdownHooks()

    companion object {
        private val application = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-boot").build().parse(
                """
                    import org.springframework.boot.autoconfigure.SpringBootApplication;
                    
                    @SpringBootApplication
                    class Application {}
                """
            )
    }

    @Test
    fun noExplicitPackagingType() = assertLoggingShutdownHook(true,
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """
    )

    @Test
    fun explicitJarPackagingType() = assertLoggingShutdownHook(true,
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>jar</packaging>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """
    )

    @Test
    fun explicitWarPackagingType() = assertLoggingShutdownHook(false,
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>war</packaging>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """
    )

    @Test
    fun lowerBootProject() = assertLoggingShutdownHook(false,
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.3.12.RELEASE</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>war</packaging>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """
    )

    @Test
    fun higherBootProject() = assertLoggingShutdownHook(false,
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.5.7</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>war</packaging>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """
    )

    private fun assertLoggingShutdownHook(isOn: Boolean, before: String) {
        val sourceFiles = MavenParser.builder().build().parse(before).plus(application)
        assertThat(LoggingShutdownHooks().run(sourceFiles).results).hasSize(if(isOn) 1 else 0)
    }
}
