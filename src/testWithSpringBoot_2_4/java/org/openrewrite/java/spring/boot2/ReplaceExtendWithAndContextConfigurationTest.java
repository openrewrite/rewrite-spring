/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Issue("https://github.com/openrewrite/rewrite-spring/issues/296")
class ReplaceExtendWithAndContextConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceExtendWithAndContextConfiguration())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot-test", "spring-test", "junit-jupiter-api", "spring-context"));
    }

    @Test
    void extendWithContextConfigurationRemovedWithConfigurationClass() {
        //language=java
        rewriteRun(
          java(
            """
              package org.example;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.test.context.ContextConfiguration;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
                            
              @ExtendWith(SpringExtension.class)
              @ContextConfiguration(classes = ExampleClass.ExampleConfiguration.class)
              public class ExampleClass {
                  @Configuration
                  static class ExampleConfiguration {
                  }
              }
              """,
            """
              package org.example;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
                            
              @SpringJUnitConfig(classes = ExampleClass.ExampleConfiguration.class)
              public class ExampleClass {
                  @Configuration
                  static class ExampleConfiguration {
                  }
              }
              """
          )
        );
    }

    @Test
    void extendWithContextConfigurationKeptWhenUsingLoaderArgument() {
        //language=java
        rewriteRun(
          java(
            """
              package org.example;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.boot.test.context.SpringBootContextLoader;
              import org.springframework.test.context.ContextConfiguration;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
                            
              @ExtendWith(SpringExtension.class)
              @ContextConfiguration(loader = SpringBootContextLoader.class)
              public class ExampleClass {
              }
              """
          )
        );
    }

    @Test
    void extendWithContextConfigurationUsesExplicitValueExplicitArray() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(value = {"classpath:beans.xml"})
            """,
          """
            @SpringJUnitConfig(locations = {"classpath:beans.xml"})
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesExplicitValueImplicitArray() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(value = "classpath:beans.xml")
            """,
          """
            @SpringJUnitConfig(locations = "classpath:beans.xml")
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesImplicitValueExplicitArray() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration({"classpath:beans.xml"})
            """,
          """
            @SpringJUnitConfig(locations = {"classpath:beans.xml"})
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesImplicitValueImplicitArray() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration("classpath:beans.xml")
            """,
          """
            @SpringJUnitConfig(locations = "classpath:beans.xml")
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesImplicitValueExplicitArrayMultipleValues() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration({"classpath:beans.xml", "classpath:more-beans.xml"})
            """,
          """
            @SpringJUnitConfig(locations = {"classpath:beans.xml", "classpath:more-beans.xml"})
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesExplicitValueExplicitArrayMultipleValues() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(value = {"classpath:beans.xml", "classpath:more-beans.xml"})
            """,
          """
            @SpringJUnitConfig(locations = {"classpath:beans.xml", "classpath:more-beans.xml"})
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesExplicitValueExplicitArrayAndAdditionalArgumentsAfter() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(value = {"classpath:beans.xml"}, inheritLocations = false)
            """,
          """
            @SpringJUnitConfig(locations = {"classpath:beans.xml"}, inheritLocations = false)
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesExplicitValueExplicitArrayAndAdditionalArgumentsBeforeAndAfter() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(inheritInitializers = true, value = {"classpath:beans.xml"}, inheritLocations = false)
            """,
          """
            @SpringJUnitConfig(inheritInitializers = true, locations = {"classpath:beans.xml"}, inheritLocations = false)
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesAllSupportedContextConfigurationAttributesWithValueAttribute() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(
                value = {"classpath:beans.xml"},
                classes = {},
                initializers = {},
                inheritLocations = true,
                inheritInitializers = false,
                name = "name"
            )
            """,
          """
            @SpringJUnitConfig(
                    locations = {"classpath:beans.xml"},
                    classes = {},
                    initializers = {},
                    inheritLocations = true,
                    inheritInitializers = false,
                    name = "name"
            )
            """
        );
    }

    @Test
    void extendWithContextConfigurationUsesAllSupportedContextConfigurationAttributesWithLocationsAttribute() {
        doExtendWithContextConfigurationTest(
          """
            @ContextConfiguration(
                classes = {},
                initializers = {},
                inheritLocations = true,
                inheritInitializers = false,
                name = "name",
                locations = {"classpath:beans.xml"}
            )
            """,
          """
            @SpringJUnitConfig(
                    classes = {},
                    initializers = {},
                    inheritLocations = true,
                    inheritInitializers = false,
                    name = "name",
                    locations = {"classpath:beans.xml"}
            )
            """
        );
    }

    void doExtendWithContextConfigurationTest(String contextConfigurationAnnotation, String springJunitConfigAnnotation) {
        //language=java
        rewriteRun(
          java(
            """
              package org.example;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.springframework.test.context.ContextConfiguration;
              import org.springframework.test.context.junit.jupiter.SpringExtension;
                            
              @ExtendWith(SpringExtension.class)
              """ + contextConfigurationAnnotation + """
              public class ExampleClass {
              }
              """,
            """
              package org.example;
                            
              import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
              
              """ + springJunitConfigAnnotation + """
              public class ExampleClass {
              }
              """
          )
        );
    }
}
