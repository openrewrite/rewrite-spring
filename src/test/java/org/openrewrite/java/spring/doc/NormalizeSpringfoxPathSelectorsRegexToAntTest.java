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
package org.openrewrite.java.spring.doc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcMainJava;

class NormalizeSpringfoxPathSelectorsRegexToAntTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NormalizeSpringfoxPathSelectorsRegexToAnt())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-core-5",
            "spring-context-5",
            "spring-beans-5",
            "spring-plugin-core",
            "springfox-core",
            "springfox-spring-web",
            "springfox-spi"));
    }

    @DocumentExample
    @Test
    void rewriteSafeRegexPrefixToAnt() {
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
                                .apis(RequestHandlerSelectors.basePackage("com.example"))
                                .paths(PathSelectors.regex("/api/v1/.*"))
                                .build();
                    }
                }
                """,
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
                                .apis(RequestHandlerSelectors.basePackage("com.example"))
                                .paths(PathSelectors.ant("/api/v1/**"))
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void leaveRegexWithCharacterClassAlone() {
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
                                .apis(RequestHandlerSelectors.basePackage("com.example"))
                                .paths(PathSelectors.regex("/api/v[0-9]+/.*"))
                                .build();
                    }
                }
                """
            )
          )
        );
    }

    @CsvSource(delimiter = '|', value = {
      "'.*'                | '/**'",
      "'/.*'               | '/**'",
      "'^.*$'              | '/**'",
      "'/api/v1/.*'        | '/api/v1/**'",
      "'^/api/v1/.*$'      | '/api/v1/**'",
      "'/api-v1/foo/.*'    | '/api-v1/foo/**'",
    })
    @ParameterizedTest
    void safeShapesAreTranslated(String regex, String ant) {
        assertThat(NormalizeSpringfoxPathSelectorsRegexToAnt.toAntIfSafe(regex)).isEqualTo(ant);
    }

    @CsvSource(delimiter = '|', value = {
      "'/api/v[0-9]+/.*'",
      "'/api/(v1|v2)/.*'",
      "'/api/v1/.*/foo'",
      "'/api/v1/foo\\\\.json'",
      "'/api/v1/foo.*bar'",
      "'/api/v1'",
    })
    @ParameterizedTest
    void unsafeShapesAreLeftAlone(String regex) {
        assertThat(NormalizeSpringfoxPathSelectorsRegexToAnt.toAntIfSafe(regex)).isNull();
    }
}
