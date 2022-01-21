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
import org.openrewrite.maven.MavenRecipeTest

class MessagesInTheDefaultErrorViewTest : MavenRecipeTest {
    override val recipe: Recipe
        get() = MessagesInTheDefaultErrorView()

    @Test
    fun `should find pom with transitive dependency to spring-webmvc`() = assertChanged(
        before =
        """<?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """,
        after = """<!--~~(MessagesInTheDefaultErrorView)~~>--><?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project><!--~~(MessagesInTheDefaultErrorView)~~>-->
        """
    )

    @Test
    fun `should find pom with declared dependency to spring-webmvc`() = assertChanged(
        before =
        """<?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-webmvc</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """,
        after = """<!--~~(MessagesInTheDefaultErrorView)~~>--><?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-webmvc</artifactId>
                    </dependency>
                </dependencies>
            </project><!--~~(MessagesInTheDefaultErrorView)~~>-->
        """
    )


    @Test
    fun `should find pom with transitive dependency to spring-webflux`() = assertChanged(
        before =
        """<?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-webflux</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """,
        after = """<!--~~(MessagesInTheDefaultErrorView)~~>--><?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-webflux</artifactId>
                    </dependency>
                </dependencies>
            </project><!--~~(MessagesInTheDefaultErrorView)~~>-->
        """
    )

    @Test
    fun `should find pom with declared dependency to spring-webflux in any version`() = assertChanged(
        before =
        """<?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-webflux</artifactId>
                        <version>5.3.15</version>
                    </dependency>
                </dependencies>
            </project>
            """,
        after = """<!--~~(MessagesInTheDefaultErrorView)~~>--><?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.4.13</version>
                    <relativePath/> <!-- lookup parent from repository -->
                </parent>
                <groupId>com.example</groupId>
                <artifactId>acme</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <properties>
                    <java.version>11</java.version>
                </properties>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-webflux</artifactId>
                        <version>5.3.15</version>
                    </dependency>
                </dependencies>
            </project><!--~~(MessagesInTheDefaultErrorView)~~>-->
        """
    )

}