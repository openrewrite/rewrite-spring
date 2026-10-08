/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.flyway;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class MigrateToFlyway10Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/flyway-10.yml",
          "org.openrewrite.java.flyway.MigrateToFlyway10"
        );
    }

    @DocumentExample
    @Test
    void addPostgresDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.postgresql</groupId>
              			<artifactId>postgresql</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """,
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-database-postgresql</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.postgresql</groupId>
              			<artifactId>postgresql</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void addMySQLDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.mysql</groupId>
              			<artifactId>mysql-connector-j</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """,
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-mysql</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.mysql</groupId>
              			<artifactId>mysql-connector-j</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void addOracleDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.oracle.database.jdbc</groupId>
              			<artifactId>ojdbc11</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """,
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-database-oracle</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.oracle.database.jdbc</groupId>
              			<artifactId>ojdbc11</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void addSqlServerDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.microsoft.sqlserver</groupId>
              			<artifactId>mssql-jdbc</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """,
            """
              <project>
              	<modelVersion>4.0.0</modelVersion>
              	<parent>
              		<groupId>org.springframework.boot</groupId>
              		<artifactId>spring-boot-starter-parent</artifactId>
              		<version>3.3.12</version>
              		<relativePath/>
              	</parent>
              	<groupId>com.example</groupId>
              	<artifactId>demo</artifactId>
              	<version>0.0.1-SNAPSHOT</version>
              	<dependencies>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-core</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>org.flywaydb</groupId>
              			<artifactId>flyway-sqlserver</artifactId>
              		</dependency>
              		<dependency>
              			<groupId>com.microsoft.sqlserver</groupId>
              			<artifactId>mssql-jdbc</artifactId>
              			<scope>runtime</scope>
              		</dependency>
              	</dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/1052")
    @Test
    void addPostgresDependencyWithTestScope() {
        assertFlywayModuleAddedWithTestScope(
          "org.postgresql",
          "postgresql",
          "flyway-database-postgresql"
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/1052")
    @Test
    void preserveExistingPostgresDependencyTestScope() {
        rewriteRun(
          //language=xml
          pomXml(
            pom(
              "<project>",
              "\t<modelVersion>4.0.0</modelVersion>",
              "\t<parent>",
              "\t\t<groupId>org.springframework.boot</groupId>",
              "\t\t<artifactId>spring-boot-starter-parent</artifactId>",
              "\t\t<version>3.3.12</version>",
              "\t\t<relativePath/>",
              "\t</parent>",
              "\t<groupId>com.example</groupId>",
              "\t<artifactId>demo</artifactId>",
              "\t<version>0.0.1-SNAPSHOT</version>",
              "\t<dependencies>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-core</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-database-postgresql</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.postgresql</groupId>",
              "\t\t\t<artifactId>postgresql</artifactId>",
              "\t\t\t<scope>runtime</scope>",
              "\t\t</dependency>",
              "\t</dependencies>",
              "</project>"
            ),
            pom(
              "<project>",
              "\t<modelVersion>4.0.0</modelVersion>",
              "\t<parent>",
              "\t\t<groupId>org.springframework.boot</groupId>",
              "\t\t<artifactId>spring-boot-starter-parent</artifactId>",
              "\t\t<version>3.3.12</version>",
              "\t\t<relativePath/>",
              "\t</parent>",
              "\t<groupId>com.example</groupId>",
              "\t<artifactId>demo</artifactId>",
              "\t<version>0.0.1-SNAPSHOT</version>",
              "\t<dependencies>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-core</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-database-postgresql</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.postgresql</groupId>",
              "\t\t\t<artifactId>postgresql</artifactId>",
              "\t\t\t<scope>runtime</scope>",
              "\t\t</dependency>",
              "\t</dependencies>",
              "</project>"
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/1052")
    @Test
    void addMySQLDependencyWithTestScope() {
        assertFlywayModuleAddedWithTestScope(
          "com.mysql",
          "mysql-connector-j",
          "flyway-mysql"
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/1052")
    @Test
    void addOracleDependencyWithTestScope() {
        assertFlywayModuleAddedWithTestScope(
          "com.oracle.database.jdbc",
          "ojdbc11",
          "flyway-database-oracle"
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/1052")
    @Test
    void addSqlServerDependencyWithTestScope() {
        assertFlywayModuleAddedWithTestScope(
          "com.microsoft.sqlserver",
          "mssql-jdbc",
          "flyway-sqlserver"
        );
    }

    private void assertFlywayModuleAddedWithTestScope(String databaseGroupId, String databaseArtifactId, String flywayModuleArtifactId) {
        rewriteRun(
          //language=xml
          pomXml(
            pom(
              "<project>",
              "\t<modelVersion>4.0.0</modelVersion>",
              "\t<parent>",
              "\t\t<groupId>org.springframework.boot</groupId>",
              "\t\t<artifactId>spring-boot-starter-parent</artifactId>",
              "\t\t<version>3.3.12</version>",
              "\t\t<relativePath/>",
              "\t</parent>",
              "\t<groupId>com.example</groupId>",
              "\t<artifactId>demo</artifactId>",
              "\t<version>0.0.1-SNAPSHOT</version>",
              "\t<dependencies>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-core</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              ("\t\t\t<groupId>%s</groupId>").formatted(databaseGroupId),
              ("\t\t\t<artifactId>%s</artifactId>").formatted(databaseArtifactId),
              "\t\t\t<scope>runtime</scope>",
              "\t\t</dependency>",
              "\t</dependencies>",
              "</project>"
            ),
            pom(
              "<project>",
              "\t<modelVersion>4.0.0</modelVersion>",
              "\t<parent>",
              "\t\t<groupId>org.springframework.boot</groupId>",
              "\t\t<artifactId>spring-boot-starter-parent</artifactId>",
              "\t\t<version>3.3.12</version>",
              "\t\t<relativePath/>",
              "\t</parent>",
              "\t<groupId>com.example</groupId>",
              "\t<artifactId>demo</artifactId>",
              "\t<version>0.0.1-SNAPSHOT</version>",
              "\t<dependencies>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              ("\t\t\t<artifactId>%s</artifactId>").formatted(flywayModuleArtifactId),
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              "\t\t\t<groupId>org.flywaydb</groupId>",
              "\t\t\t<artifactId>flyway-core</artifactId>",
              "\t\t\t<scope>test</scope>",
              "\t\t</dependency>",
              "\t\t<dependency>",
              ("\t\t\t<groupId>%s</groupId>").formatted(databaseGroupId),
              ("\t\t\t<artifactId>%s</artifactId>").formatted(databaseArtifactId),
              "\t\t\t<scope>runtime</scope>",
              "\t\t</dependency>",
              "\t</dependencies>",
              "</project>"
            )
          )
        );
    }

    private static String pom(String... lines) {
        return String.join("\n", lines) + "\n";
    }
}
