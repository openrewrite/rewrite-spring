/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringCloudStreamVersionUpgradeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7");
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/672")
    @Test
    void upgradesSpringCloudStreamBom() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>1.5.2.RELEASE</version>
                      <relativePath/>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <spring-cloud-stream.version>Fishtown.SR4</spring-cloud-stream.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.cloud</groupId>
                              <artifactId>spring-cloud-stream-dependencies</artifactId>
                              <version>${spring-cloud-stream.version}</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.after(actual ->
              assertThat(actual)
                .containsPattern("<spring-cloud-stream.version>3\\.2\\.\\d+</spring-cloud-stream.version>")
                .actual()
            )
          )
        );
    }

    @Test
    void doesNotDowngradeNewerSpringCloudStreamBom() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.springframework.cloud</groupId>
                              <artifactId>spring-cloud-stream-dependencies</artifactId>
                              <version>4.0.0</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }
}
