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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateLiquibasePropertiesApiTest implements RewriteTest {
    private static final String SPRING_LIQUIBASE = """
      package liquibase.integration.spring;
      public class SpringLiquibase {
          public void setContexts(String contexts) {}
          public void setLabels(String labels) {}
          public void setLabelFilter(String labels) {}
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLiquibasePropertiesApi())
          .parser(JavaParser.fromJavaVersion().dependsOn(
            SPRING_LIQUIBASE,
            """
              package org.springframework.boot.autoconfigure.liquibase;
              public class LiquibaseProperties {
                  public String getContexts() { return null; }
                  public String getLabels() { return null; }
              }
              """
          ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.springframework.boot.autoconfigure.liquibase", "org.springframework.boot.liquibase.autoconfigure"})
    void migrateScalarPropertySetters(String propertyPackage) {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(SPRING_LIQUIBASE,
            "package " + propertyPackage + "; public class LiquibaseProperties { " +
            "public String getContexts() { return null; } public String getLabels() { return null; } }")),
          java(
            """
              import liquibase.integration.spring.SpringLiquibase;
              import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;

              class Config {
                  void configure(SpringLiquibase liquibase, LiquibaseProperties properties) {
                      liquibase.setContexts(properties.getContexts());
                      liquibase.setLabels(properties.getLabels());
                  }
              }
              """.replace("org.springframework.boot.autoconfigure.liquibase", propertyPackage),
            """
              import liquibase.integration.spring.SpringLiquibase;
              import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
              import org.springframework.util.StringUtils;

              class Config {
                  void configure(SpringLiquibase liquibase, LiquibaseProperties properties) {
                      liquibase.setContexts(StringUtils.collectionToCommaDelimitedString(properties.getContexts()));
                      liquibase.setLabelFilter(StringUtils.collectionToCommaDelimitedString(properties.getLabelFilter()));
                  }
              }
              """.replace("org.springframework.boot.autoconfigure.liquibase", propertyPackage)
          )
        );
    }

    @Test
    void preserveAlreadyCollectionAwareCode() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-core-6")
            .dependsOn(SPRING_LIQUIBASE,
              """
                package org.springframework.boot.liquibase.autoconfigure;
                import java.util.List;
                public class LiquibaseProperties {
                    public List<String> getContexts() { return null; }
                    public List<String> getLabelFilter() { return null; }
                }
                """
            )),
          java(
            """
              import liquibase.integration.spring.SpringLiquibase;
              import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
              import org.springframework.util.StringUtils;

              class Config {
                  void configure(SpringLiquibase liquibase, LiquibaseProperties properties) {
                      liquibase.setContexts(StringUtils.collectionToCommaDelimitedString(properties.getContexts()));
                      liquibase.setLabelFilter(StringUtils.collectionToCommaDelimitedString(properties.getLabelFilter()));
                  }
              }
              """
          )
        );
    }

    @Test
    void renameLiteralLabelsAndPreserveUnrelatedContexts() {
        rewriteRun(
          java(
            """
              import liquibase.integration.spring.SpringLiquibase;

              class Config {
                  String getContexts() { return "test"; }
                  void configure(SpringLiquibase liquibase) {
                      liquibase.setContexts(getContexts());
                      liquibase.setLabels("test");
                  }
              }
              """,
            """
              import liquibase.integration.spring.SpringLiquibase;

              class Config {
                  String getContexts() { return "test"; }
                  void configure(SpringLiquibase liquibase) {
                      liquibase.setContexts(getContexts());
                      liquibase.setLabelFilter("test");
                  }
              }
              """
          )
        );
    }
}
