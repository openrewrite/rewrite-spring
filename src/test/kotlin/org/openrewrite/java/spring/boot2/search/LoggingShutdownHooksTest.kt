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
package org.openrewrite.java.spring.org.openrewrite.java.spring.boot2.search

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.spring.boot2.search.LoggingShutdownHooks
import org.openrewrite.maven.MavenRecipeTest

/**
 * @author Alex Boyko
 */
class LoggingShutdownHooksTest : MavenRecipeTest {

    override val recipe: Recipe
        get() = LoggingShutdownHooks()

    @Test
    fun noExplicitPackagingType() = assertChanged(
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
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """,
        after = """
            <!--~~>--><project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.5.7</version>
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
            </project><!--~~>-->
        """
    )

    @Test
    fun explicitJarPackagingType() = assertChanged(
        before = """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.5.7</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>jar</packaging>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """,
        after = """
            <!--~~>--><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.5.7</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <packaging>jar</packaging>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project><!--~~>-->
        """
    )

    @Test
    fun explicitWarPackagingType() = assertUnchanged(
        before = """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
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
                <properties>
                    <java.version>11</java.version>
                </properties>
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
    fun lowerBootProject() = assertUnchanged(
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.9</version>
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
    fun higherBootProject() = assertUnchanged(
        before = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.6.3</version>
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
}
