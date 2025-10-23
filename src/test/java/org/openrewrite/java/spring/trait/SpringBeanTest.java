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
package org.openrewrite.java.spring.trait;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class SpringBeanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.toRecipe(() -> new SpringBean.Matcher()
            .asVisitor(bean -> SearchResult.found(bean.getTree(), bean.getName()))))
          .parser(JavaParser.fromJavaVersion().dependsOn(
            //language=java
            """
              package org.springframework.context.annotation;

              public @interface Bean {}
              """,
            //language=java
            """
              package org.springframework.context.annotation;

              public @interface Configuration {}
              """,
            //language=java
            """
              package com.example;

              public class UserService { }
              """
          ));
    }

    @DocumentExample
    @SuppressWarnings("SpringXmlModelInspection")
    @Test
    void xmlConfiguration() {
        rewriteRun(
          xml(
            //language=xml
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <beans xmlns="http://www.springframework.org/schema/beans"
                  xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                <bean id="testBean" class="org.springframework.beans.TestBean" scope="prototype">
                  <property name="age" value="10"/>
                  <property name="sibling">
                      <bean class="org.springframework.beans.TestBean">
                          <property name="age" value="11"/>
                      </bean>
                  </property>
                </bean>
              </beans>
              """,
            //language=xml
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <beans xmlns="http://www.springframework.org/schema/beans"
                  xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">
                <!--~~(testBean)~~>--><bean id="testBean" class="org.springframework.beans.TestBean" scope="prototype">
                  <property name="age" value="10"/>
                  <property name="sibling">
                      <bean class="org.springframework.beans.TestBean">
                          <property name="age" value="11"/>
                      </bean>
                  </property>
                </bean>
              </beans>
              """
          )
        );
    }

    @Nested
    class JavaConfig {
        @Test
        void defaultName() {
            rewriteRun(
              java(
                //language=java
                """
                  package com.example;

                  import org.springframework.context.annotation.Bean;
                  import org.springframework.context.annotation.Configuration;

                  @Configuration
                  class BeansConfiguration {
                      @Bean
                      UserService userService() {
                          return new UserService();
                      }
                  }
                  """,
                //language=java
                """
                  package com.example;

                  import org.springframework.context.annotation.Bean;
                  import org.springframework.context.annotation.Configuration;

                  @Configuration
                  class BeansConfiguration {
                      /*~~(userService)~~>*/@Bean
                      UserService userService() {
                          return new UserService();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void explicitName() {
            rewriteRun(
              java(
                //language=java
                """
                  package com.example;

                  import org.springframework.context.annotation.Bean;
                  import org.springframework.context.annotation.Configuration;

                  @Configuration
                  class BeansConfiguration {
                      @Bean(name="testName")
                      UserService userService() {
                          return new UserService();
                      }
                  }
                  """,
                //language=java
                """
                  package com.example;

                  import org.springframework.context.annotation.Bean;
                  import org.springframework.context.annotation.Configuration;

                  @Configuration
                  class BeansConfiguration {
                      /*~~(testName)~~>*/@Bean(name="testName")
                      UserService userService() {
                          return new UserService();
                      }
                  }
                  """
              )
            );
        }
    }

}
