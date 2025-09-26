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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateDocketBeanToGroupedOpenApiBeanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDocketBeanToGroupedOpenApiBean())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-core",
              "spring-context",
              "spring-beans",
//              "spring-plugin-core",
              "springfox-core",
              "springfox-spring-web",
              "springfox-spi"));
    }

    @DocumentExample
    @Test
    void rewriteSingleDocketToApplicationYml() {
        rewriteRun(
          //language=yaml
          srcMainResources(
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                springdoc:
                  api-docs:
                    path: /v3/api-docs
                  swagger-ui:
                    path: /swagger-ui.html
                  paths-to-match: "/**"
                """,
              spec -> spec.path("application.yaml")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .select()
                                .apis(RequestHandlerSelectors.any())
                                .paths(PathSelectors.any())
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                class ApplicationConfiguration {
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketToApplicationProperties() {
        rewriteRun(
          //language=properties
          srcMainResources(
            properties(
              """
                spring.application.name = main
                """,
              """
                spring.application.name = main
                springdoc.api-docs.path=/v3/api-docs
                springdoc.paths-to-match=/**
                springdoc.swagger-ui.path=/swagger-ui.html
                """,
              spec -> spec.path("application.properties")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .select()
                                .apis(RequestHandlerSelectors.any())
                                .paths(PathSelectors.any())
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                class ApplicationConfiguration {
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketToGroupedOpenApiBean() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .select()
                                .apis(RequestHandlerSelectors.any())
                                .paths(PathSelectors.any())
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                import org.springdoc.core.models.GroupedOpenApi;
                import org.springframework.context.annotation.Bean;

                class ApplicationConfiguration {

                    @Bean
                    public GroupedOpenApi publicApi() {
                        return GroupedOpenApi.builder()
                                .group("public")
                                .pathsToMatch("/**")
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketWithLiteralValuesToApplicationYml() {
        rewriteRun(
          //language=yaml
          srcMainResources(
            yaml(
              """
                spring.application.name: main
                """,
              """
                spring.application.name: main
                springdoc:
                  api-docs:
                    path: /v3/api-docs
                  swagger-ui:
                    path: /swagger-ui.html
                  group-configs[0]:
                    group: internal
                    paths-to-match: "/api/v1/**"
                    packages-to-scan: "com.example.api"
                """,
              spec -> spec.path("application.yaml")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName("internal")
                                .select()
                                .apis(RequestHandlerSelectors.basePackage("com.example.api"))
                                .paths(PathSelectors.ant("/api/v1/**"))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                class ApplicationConfiguration {
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleWithLiteralValuesToApplicationProperties() {
        rewriteRun(
          //language=properties
          srcMainResources(
            properties(
              """
                spring.application.name = main
                """,
              """
                spring.application.name = main
                springdoc.api-docs.path=/v3/api-docs
                springdoc.group-configs[0].group=internal
                springdoc.group-configs[0].packages-to-scan="com.example.api"
                springdoc.group-configs[0].paths-to-match="/api/v1/**"
                springdoc.swagger-ui.path=/swagger-ui.html
                """,
              spec -> spec.path("application.properties")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName("internal")
                                .select()
                                .apis(RequestHandlerSelectors.basePackage("com.example.api"))
                                .paths(PathSelectors.ant("/api/v1/**"))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                class ApplicationConfiguration {
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketWithLiteralValuesToGroupedOpenApiBean() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {
                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName("internal")
                                .select()
                                .apis(RequestHandlerSelectors.basePackage("com.example.api"))
                                .paths(PathSelectors.ant("/api/v1/**"))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                import org.springdoc.core.models.GroupedOpenApi;
                import org.springframework.context.annotation.Bean;

                class ApplicationConfiguration {

                    @Bean
                    public GroupedOpenApi publicApi() {
                        return GroupedOpenApi.builder()
                                .group("internal")
                                .pathsToMatch("/api/v1/**")
                                .packagesToScan("com.example.api")
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketWithFieldValuesAndApplicationYmlToGroupedOpenApiBean() {
        rewriteRun(
          //language=yaml
          srcMainResources(
            yaml(
              """
                spring.application.name: main
                """,
              spec -> spec.path("application.yaml")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName(GROUPNAME)
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(PathSelectors.ant(PATH))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                import org.springdoc.core.models.GroupedOpenApi;
                import org.springframework.context.annotation.Bean;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public GroupedOpenApi publicApi() {
                        return GroupedOpenApi.builder()
                                .group(GROUPNAME)
                                .pathsToMatch(PATH)
                                .packagesToScan(BASEPACKAGE)
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketWithFieldValuesAndApplicationPropertiesToGroupedOpenApiBean() {
        rewriteRun(
          //language=properties
          srcMainResources(
            properties(
              """
                spring.application.name = main
                """,
              spec -> spec.path("application.properties")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName(GROUPNAME)
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(PathSelectors.ant(PATH))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                import org.springdoc.core.models.GroupedOpenApi;
                import org.springframework.context.annotation.Bean;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public GroupedOpenApi publicApi() {
                        return GroupedOpenApi.builder()
                                .group(GROUPNAME)
                                .pathsToMatch(PATH)
                                .packagesToScan(BASEPACKAGE)
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void rewriteSingleDocketWithFieldValuesToGroupedOpenApiBean() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName(GROUPNAME)
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(PathSelectors.ant(PATH))
                                .build()
                                .pathMapping("/");
                    }
                }
                """,
              """
                package org.project.example;

                import org.springdoc.core.models.GroupedOpenApi;
                import org.springframework.context.annotation.Bean;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public GroupedOpenApi publicApi() {
                        return GroupedOpenApi.builder()
                                .group(GROUPNAME)
                                .pathsToMatch(PATH)
                                .packagesToScan(BASEPACKAGE)
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void ignoreMultipleDocket() {
        rewriteRun(
          //language=yaml
          srcMainResources(
            yaml(
              """
                spring.application.name: main
                """,
              spec -> spec.path("application.yaml")
            )
          ),
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName(GROUPNAME)
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(PathSelectors.ant(PATH))
                                .build()
                                .pathMapping("/");
                    }

                    @Bean
                    public Docket internalApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName("internal")
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(PathSelectors.ant(PATH))
                                .build()
                                .pathMapping("/");
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void ignoreDocketWhenRequestHandlerSelectorsExternal() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.RequestHandler;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;
                import java.util.function.Predicate;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final Predicate<RequestHandler> requestHandlerPredicate = RequestHandlerSelectors.basePackage(BASEPACKAGE);
                    private static final String PATH = "/api/v1/**";

                    @Bean
                    public Docket publicApi() {
                       return new Docket(DocumentationType.SWAGGER_2)
                               .groupName(GROUPNAME)
                               .select()
                               .apis(requestHandlerPredicate)
                               .paths(PathSelectors.ant(PATH))
                               .build()
                               .pathMapping("/");
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void ignoreDocketWhenPathSelectorsRegex() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.RequestHandler;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;
                import java.util.function.Predicate;

                class ApplicationConfiguration {

                    @Bean
                    public Docket publicApi() {
                       return new Docket(DocumentationType.SWAGGER_2)
                               .select()
                               .apis(RequestHandlerSelectors.basePackage("com.example"))
                               .paths(PathSelectors.regex(""))
                               .build()
                               .pathMapping("/");
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void ignoreDocketWhenPathSelectorsExternal() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                package org.project.example;

                import org.springframework.context.annotation.Bean;
                import springfox.documentation.builders.PathSelectors;
                import springfox.documentation.builders.RequestHandlerSelectors;
                import springfox.documentation.spi.DocumentationType;
                import springfox.documentation.spring.web.plugins.Docket;
                import java.util.function.Predicate;

                class ApplicationConfiguration {

                    private static final String GROUPNAME = "internal";
                    private static final String BASEPACKAGE = "com.example.api";
                    private static final String PATH = "/api/v1/**";
                    private static final Predicate<String> pathSelectorsPredicate = PathSelectors.ant(PATH);

                    @Bean
                    public Docket publicApi() {
                        return new Docket(DocumentationType.SWAGGER_2)
                                .groupName(GROUPNAME)
                                .select()
                                .apis(RequestHandlerSelectors.basePackage(BASEPACKAGE))
                                .paths(pathSelectorsPredicate)
                                .build()
                                .pathMapping("/");
                    }
                }
                """
            )
          )
        );
    }
}
