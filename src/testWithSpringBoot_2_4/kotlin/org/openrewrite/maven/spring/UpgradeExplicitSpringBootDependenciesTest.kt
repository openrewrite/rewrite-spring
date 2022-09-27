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
package org.openrewrite.maven.spring

import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.MavenRecipeTest
import org.openrewrite.maven.tree.MavenResolutionResult
import org.openrewrite.maven.tree.ResolvedDependency
import org.openrewrite.maven.tree.Scope
import org.openrewrite.test.RewriteTest

class UpgradeExplicitSpringBootDependenciesTest : MavenRecipeTest, RewriteTest {

    @Test
    fun shouldUpdateExplicitDepenciesTo30() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3"))
            .expectedCyclesThatMakeChanges(1)},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml(
                """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>2.7.3</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>4.2.8</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>2.7.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.0.0-M3</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>4.2.9</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>3.0.0-M3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
        )
    )

    @Test
    fun shouldNotUpdateIfNoSpringDependencies() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3"))},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>

                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>

                    <dependencies>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>4.2.8</version>
                        </dependency>
                    </dependencies>
                </project>
            """)
        )
    )

    @Test
    fun shouldNotUpdateForOldVersion() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("3.0.0-M3", "2.7.+"))},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>

                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>2.6.0</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>4.2.8</version>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
            """)
        )
    )

    @Test
    fun shouldNotUpdateIfParentAndNoExplicitDeps() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3"))},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                   <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.1</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                                    
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>

                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
            """)
        )
    )

    @Test
    fun shouldUpdateIfSpringParentAndExplicitDependency() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3")).expectedCyclesThatMakeChanges(1)},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.1</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>2.7.3</springboot.version>
                    </properties>
                
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.1</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>3.0.0-M3</springboot.version>
                    </properties>
                
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """)
        )
    )

    @Test
    fun shouldNotUpdateIfDependencyImportAndNoExplicitDeps() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3"))},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>

                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>

                    <dependencyManagement>
                         <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>2.7.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                        </dependency>
                    </dependencies>
                </project>
        """)
        )
    )

    @Test
    fun shouldUpdateIfSpringDependencyManagementAndExplicitVersion() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3")).expectedCyclesThatMakeChanges(3)},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml("""
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>explicit-deps-app</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <name>explicit-deps-app</name>
                        <description>explicit-deps-app</description>
                        <properties>
                            <java.version>17</java.version>
                            <maven.compiler.source>17</maven.compiler.source>
                            <maven.compiler.target>17</maven.compiler.target>
                        </properties>
                        <repositories>
                            <repository>
                                <id>spring-milestone</id>
                                <url>https://repo.spring.io/milestone</url>
                                <snapshots>
                                    <enabled>false</enabled>
                                </snapshots>
                            </repository>
                        </repositories>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-dependencies</artifactId>
                                    <version>2.7.0</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-web</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>io.dropwizard.metrics</groupId>
                                <artifactId>metrics-annotation</artifactId>
                                <version>4.2.8</version>
                            </dependency>
                        </dependencies>
                    </project>
        """,
        """
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>explicit-deps-app</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <name>explicit-deps-app</name>
                        <description>explicit-deps-app</description>
                        <properties>
                            <java.version>17</java.version>
                            <maven.compiler.source>17</maven.compiler.source>
                            <maven.compiler.target>17</maven.compiler.target>
                        </properties>
                        <repositories>
                            <repository>
                                <id>spring-milestone</id>
                                <url>https://repo.spring.io/milestone</url>
                                <snapshots>
                                    <enabled>false</enabled>
                                </snapshots>
                            </repository>
                        </repositories>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-dependencies</artifactId>
                                    <version>2.7.0</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-web</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>io.dropwizard.metrics</groupId>
                                <artifactId>metrics-annotation</artifactId>
                                <version>4.2.9</version>
                            </dependency>
                        </dependencies>
                    </project>
                """)
        )
    )

    @Test
    fun shouldUpdateWithVersionsAsProperty() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3")).expectedCyclesThatMakeChanges(1)},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>2.7.3</springboot.version>
                        <metrics-annotation.version>4.2.8</metrics-annotation.version>
                    </properties>
                
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>${'$'}{metrics-annotation.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>${'$'}{springboot.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
        """,
        """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>3.0.0-M3</springboot.version>
                        <metrics-annotation.version>4.2.9</metrics-annotation.version>
                    </properties>
                
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>${'$'}{metrics-annotation.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>${'$'}{springboot.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """)
        )
    )

    @Test
    fun shouldNotTouchNewerVersions() = rewriteRun(
        { spec -> spec.recipe(UpgradeExplicitSpringBootDependencies("2.7.X", "3.0.0-M3")).expectedCyclesThatMakeChanges(1)},
        org.openrewrite.java.Assertions.mavenProject(
            "project",
            org.openrewrite.java.Assertions.srcMainJava(
                org.openrewrite.java.Assertions.java("class A{}")
            ),
            org.openrewrite.maven.Assertions.pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>2.7.3</springboot.version>
                        <metrix.annotation.version>4.2.12</metrix.annotation.version>
                    </properties>
                    
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                    
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>${'$'}{metrix.annotation.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>3.0.0-M3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
        """,
        """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <springboot.version>3.0.0-M3</springboot.version>
                        <metrix.annotation.version>4.2.12</metrix.annotation.version>
                    </properties>
                    
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                    
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>${'$'}{springboot.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>${'$'}{metrix.annotation.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>3.0.0-M3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """)
        )
    )
    @Test
    fun shouldBuildCorrectPomModelAfterUpdateTo30() {
        val recipe: Recipe = UpgradeExplicitSpringBootDependencies(
            "2.7.X",
            "3.0.0-M3"
        )
        val errors: List<Throwable> = ArrayList()
        val ctx = InMemoryExecutionContext { ex: Throwable ->
            throw RuntimeException("Error due UpgradeUnmanagedSpringProject recipe: " + ex.message, ex)
        }
        val parser = MavenParser.builder().build()
        val documentList =
            parser.parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>explicit-deps-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>explicit-deps-app</name>
                    <description>explicit-deps-app</description>
                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <repositories>
                        <repository>
                            <id>spring-milestone</id>
                            <url>https://repo.spring.io/milestone</url>
                            <snapshots>
                                <enabled>false</enabled>
                            </snapshots>
                        </repository>
                    </repositories>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>2.7.3</version>
                        </dependency>
                        <dependency>
                            <groupId>io.dropwizard.metrics</groupId>
                            <artifactId>metrics-annotation</artifactId>
                            <version>4.2.8</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <version>2.7.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
            """)
        val result = recipe.run(documentList, ctx).results
        Assertions.assertThat(result).hasSize(1)
        val mavenResolutionResult = result[0].after.markers.findFirst(
            MavenResolutionResult::class.java
        )
        Assertions.assertThat(mavenResolutionResult).isPresent
        val resolvedTestDependencies =
            mavenResolutionResult.get().dependencies[Scope.Test]!!
        Assertions.assertThat(resolvedTestDependencies).isNotEmpty
        val resolvedDependencies =
            mavenResolutionResult.get().dependencies[Scope.Compile]!!
        Assertions.assertThat(resolvedDependencies).isNotEmpty
        val cSpringStarterWeb = Condition(
            { rd: ResolvedDependency -> rd.artifactId == "spring-boot-starter-web" && rd.version == "3.0.0-M3" },
            "spring-boot-starter-web in version 3.0.0-M3"
        )
        Assertions.assertThat(resolvedDependencies).haveExactly(1, cSpringStarterWeb)
        val cMetrics = Condition(
            { rd: ResolvedDependency -> rd.artifactId == "metrics-annotation" && rd.version == "4.2.9" },
            "metrics-annotation in version 4.2.9"
        )
        Assertions.assertThat(resolvedDependencies).haveExactly(1, cMetrics)
        val cSpringStarterTest = Condition(
            { rd: ResolvedDependency -> rd.artifactId == "spring-boot-starter-test" && rd.version == "3.0.0-M3" },
            "spring-boot-starter-test in version 3.0.0-M3"
        )
        Assertions.assertThat(resolvedTestDependencies).haveExactly(1, cSpringStarterTest)
    }
}
