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
}
