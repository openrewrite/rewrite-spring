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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("MethodMayBeStatic")
class ConditionalOnBeanAnyNestedConditionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConditionalOnBeanAnyNestedCondition())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot-autoconfigure", "spring-context"));
    }

    @Test
    void conditionalAnnotationSingleClassCandidateNoChange() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              
              class ConfigClass {
                  @Bean
                  @ConditionalOnBean({Aa.class})
                  public ThingOne thingOne() {
                      return new ThingOne();
                  }
                  
                  @Bean
                  @ConditionalOnBean(Aa.class)
                  public ThingTwo thingTwo() {
                      return new ThingTwo();
                  }
              }
              """
          )
        );
    }

    @Test
    void conditionalAnnotationMultipleClassCandidates() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              
              class ThingOneTwo {}
              
              class ThingOneTwoThree {}
              
              class ThingFour {}
              
              class ConfigClass {
                  @Bean
                  @ConditionalOnBean({Aa.class, Bb.class})
                  public ThingOneTwo thingOneTwo() {
                      return new ThingOneTwo();
                  }
                  
                  @Bean
                  @ConditionalOnBean({Aa.class, Bb.class})
                  public ThingOneTwoThree thingOneTwoThree() {
                      return new ThingOneTwoThree();
                  }
                  
                  @Bean
                  @ConditionalOnBean({Cc.class, Bb.class})
                  public ThingFour thingFour() {
                      return new ThingFour();
                  }
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Conditional;
              
              class ThingOneTwo {}
              
              class ThingOneTwoThree {}
              
              class ThingFour {}
              
              class ConfigClass {
                  @Bean
                  @Conditional(ConditionAaOrBb.class)
                  public ThingOneTwo thingOneTwo() {
                      return new ThingOneTwo();
                  }
              
                  @Bean
                  @Conditional(ConditionAaOrBb.class)
                  public ThingOneTwoThree thingOneTwoThree() {
                      return new ThingOneTwoThree();
                  }
              
                  @Bean
                  @Conditional(ConditionBbOrCc.class)
                  public ThingFour thingFour() {
                      return new ThingFour();
                  }
              
                  private static class ConditionAaOrBb extends AnyNestedCondition {
                      ConditionAaOrBb() {
                          super(ConfigurationPhase.REGISTER_BEAN);
                      }
              
                      @ConditionalOnBean(Aa.class)
                      class AaCondition {
                      }
              
                      @ConditionalOnBean(Bb.class)
                      class BbCondition {
                      }
                  }
              
                  private static class ConditionBbOrCc extends AnyNestedCondition {
                      ConditionBbOrCc() {
                          super(ConfigurationPhase.REGISTER_BEAN);
                      }
              
                      @ConditionalOnBean(Bb.class)
                      class BbCondition {
                      }
              
                      @ConditionalOnBean(Cc.class)
                      class CcCondition {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void twoConditionalAnnotationsWithSameMultipleClassCandidatesReversed() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              
              class ThingOne {}
              
              class ThingTwo {}
              
              class ConfigClass {
                  @Bean
                  @ConditionalOnBean({Aa.class, Bb.class})
                  public ThingOne thingOne() {
                      return new ThingOne();
                  }
                  @Bean
                  @ConditionalOnBean({Bb.class, Aa.class})
                  public ThingTwo thingTwo() {
                      return new ThingTwo();
                  }
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Conditional;
              
              class ThingOne {}
              
              class ThingTwo {}
              
              class ConfigClass {
                  @Bean
                  @Conditional(ConditionAaOrBb.class)
                  public ThingOne thingOne() {
                      return new ThingOne();
                  }
              
                  @Bean
                  @Conditional(ConditionAaOrBb.class)
                  public ThingTwo thingTwo() {
                      return new ThingTwo();
                  }
              
                  private static class ConditionAaOrBb extends AnyNestedCondition {
                      ConditionAaOrBb() {
                          super(ConfigurationPhase.REGISTER_BEAN);
                      }
              
                      @ConditionalOnBean(Aa.class)
                      class AaCondition {
                      }
              
                      @ConditionalOnBean(Bb.class)
                      class BbCondition {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleConditionalAnnotationMultipleTypeClassNames() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;

              class ThingOneTwo {
              }

              class ConfigClass {
                  @Bean
                  @ConditionalOnBean(type = {"com.foo.Aa.class", "com.foo.Bb.class"})
                  public ThingOneTwo thingOneTwo() {
                      return new ThingOneTwo();
                  }
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
              import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Conditional;

              class ThingOneTwo {
              }

              class ConfigClass {
                  @Bean
                  @Conditional(ConditionAaOrBb.class)
                  public ThingOneTwo thingOneTwo() {
                      return new ThingOneTwo();
                  }

                  private static class ConditionAaOrBb extends AnyNestedCondition {
                      ConditionAaOrBb() {
                          super(ConfigurationPhase.REGISTER_BEAN);
                      }

                      @ConditionalOnBean(type = "com.foo.Aa.class")
                      class AaCondition {
                      }

                      @ConditionalOnBean(type = "com.foo.Bb.class")
                      class BbCondition {
                      }
                  }
              }
              """
          )
        );
    }
}
