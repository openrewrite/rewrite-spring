/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.table.ConfigurationPropertiesTable;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.PathUtils.separatorsToSystem;
import static org.openrewrite.java.Assertions.java;

class FindConfigurationPropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindConfigurationProperties())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-2.+"));
    }

    @DocumentExample
    @Test
    void findConfigurationPropertiesWithValue() {
        rewriteRun(
          spec -> spec.dataTable(ConfigurationPropertiesTable.Row.class, rows -> {
              assertThat(rows).singleElement()
                .matches(row -> separatorsToSystem("test/MyProperties.java").equals(row.getSourcePath()))
                .matches(row -> "test.MyProperties".equals(row.getClassType()))
                .matches(row -> "my.service".equals(row.getPrefix()));
          }).cycles(1).expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties("my.service")
              public class MyProperties {
                  private boolean enabled;
                  private String url;

                  public boolean isEnabled() {
                      return enabled;
                  }

                  public void setEnabled(boolean enabled) {
                      this.enabled = enabled;
                  }

                  public String getUrl() {
                      return url;
                  }

                  public void setUrl(String url) {
                      this.url = url;
                  }
              }
              """,
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              /*~~(@ConfigurationProperties("my.service"))~~>*/@ConfigurationProperties("my.service")
              public class MyProperties {
                  private boolean enabled;
                  private String url;

                  public boolean isEnabled() {
                      return enabled;
                  }

                  public void setEnabled(boolean enabled) {
                      this.enabled = enabled;
                  }

                  public String getUrl() {
                      return url;
                  }

                  public void setUrl(String url) {
                      this.url = url;
                  }
              }
              """
          )
        );
    }

    @Test
    void findConfigurationPropertiesWithExplicitValue() {
        rewriteRun(
          spec -> spec.dataTable(ConfigurationPropertiesTable.Row.class, rows -> {
              assertThat(rows).singleElement()
                .matches(row -> separatorsToSystem("test/ServerProperties.java").equals(row.getSourcePath()))
                .matches(row -> "test.ServerProperties".equals(row.getClassType()))
                .matches(row -> "app.server".equals(row.getPrefix()));
          }).cycles(1).expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(value = "app.server")
              public class ServerProperties {
                  private int port;

                  public int getPort() {
                      return port;
                  }

                  public void setPort(int port) {
                      this.port = port;
                  }
              }
              """,
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              /*~~(@ConfigurationProperties("app.server"))~~>*/@ConfigurationProperties(value = "app.server")
              public class ServerProperties {
                  private int port;

                  public int getPort() {
                      return port;
                  }

                  public void setPort(int port) {
                      this.port = port;
                  }
              }
              """
          )
        );
    }

    @Test
    void findConfigurationPropertiesWithPrefix() {
        rewriteRun(
          spec -> spec.dataTable(ConfigurationPropertiesTable.Row.class, rows -> {
              assertThat(rows).singleElement()
                .matches(row -> separatorsToSystem("test/DatabaseProperties.java").equals(row.getSourcePath()))
                .matches(row -> "test.DatabaseProperties".equals(row.getClassType()))
                .matches(row -> "database.config".equals(row.getPrefix()));
          }).cycles(1).expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(prefix = "database.config")
              public class DatabaseProperties {
                  private String url;

                  public String getUrl() {
                      return url;
                  }

                  public void setUrl(String url) {
                      this.url = url;
                  }
              }
              """,
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              /*~~(@ConfigurationProperties("database.config"))~~>*/@ConfigurationProperties(prefix = "database.config")
              public class DatabaseProperties {
                  private String url;

                  public String getUrl() {
                      return url;
                  }

                  public void setUrl(String url) {
                      this.url = url;
                  }
              }
              """
          )
        );
    }

    @Test
    void findConfigurationPropertiesWithConstantReference() {
        rewriteRun(
          spec -> spec.dataTable(ConfigurationPropertiesTable.Row.class, rows -> {
              assertThat(rows).singleElement()
                .matches(row -> separatorsToSystem("test/KubernetesClientProperties.java").equals(row.getSourcePath()))
                .matches(row -> "test.KubernetesClientProperties".equals(row.getClassType()))
                .matches(row -> "spring.cloud.kubernetes.client".equals(row.getPrefix()));
          }).cycles(1).expectedCyclesThatMakeChanges(1).typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(PREFIX)
              public class KubernetesClientProperties {
                  public static final String PREFIX = "spring.cloud.kubernetes.client";

                  private String namespace;

                  public String getNamespace() {
                      return namespace;
                  }

                  public void setNamespace(String namespace) {
                      this.namespace = namespace;
                  }
              }
              """,
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              /*~~(@ConfigurationProperties(PREFIX = "spring.cloud.kubernetes.client"))~~>*/@ConfigurationProperties(PREFIX)
              public class KubernetesClientProperties {
                  public static final String PREFIX = "spring.cloud.kubernetes.client";

                  private String namespace;

                  public String getNamespace() {
                      return namespace;
                  }

                  public void setNamespace(String namespace) {
                      this.namespace = namespace;
                  }
              }
              """
          )
        );
    }

    @Test
    void findConfigurationPropertiesWithConstantReferenceInValueParam() {
        rewriteRun(
          spec -> spec.dataTable(ConfigurationPropertiesTable.Row.class, rows -> {
              assertThat(rows).singleElement()
                .matches(row -> separatorsToSystem("test/AppProperties.java").equals(row.getSourcePath()))
                .matches(row -> "test.AppProperties".equals(row.getClassType()))
                .matches(row -> "app.config".equals(row.getPrefix()));
          }).cycles(1).expectedCyclesThatMakeChanges(1).typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(value = PREFIX)
              public class AppProperties {
                  public static final String PREFIX = "app.config";

                  private String name;

                  public String getName() {
                      return name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }
              }
              """,
            """
              package test;
              import org.springframework.boot.context.properties.ConfigurationProperties;

              /*~~(@ConfigurationProperties(PREFIX = "app.config"))~~>*/@ConfigurationProperties(value = PREFIX)
              public class AppProperties {
                  public static final String PREFIX = "app.config";

                  private String name;

                  public String getName() {
                      return name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreNonConfigurationPropertiesClasses() {
        rewriteRun(
          //language=java
          java(
            """
              package test;

              public class RegularClass {
                  private String property;

                  public String getProperty() {
                      return property;
                  }

                  public void setProperty(String property) {
                      this.property = property;
                  }
              }
              """
          )
        );
    }
}
