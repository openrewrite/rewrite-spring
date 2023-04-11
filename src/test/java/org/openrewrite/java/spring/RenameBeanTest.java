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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RenameBeanTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RenameBean("sample.MyType", "foo", "bar"))
          .parser(
            JavaParser.fromJavaVersion()
              .logCompilationWarningsAndErrors(true)
              .dependsOn(
                """
                      package sample;
                      class MyType {}
                  """, """
                      package sample;
                      @interface MyAnnotation {
                          String value() default "";
                      }
                  """
              )
              .classpathFromResources(new InMemoryExecutionContext(), "spring-context-6.0.7", "spring-beans-6.0.7")
          );
    }

    @Nested
    class Declarations {

        @Nested
        class BeanMethods {
            @Test
            void impliedName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        public MyType foo() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        public MyType bar() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void explicitNameValueParam() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(value = "foo")
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(value = "bar")
                        public MyType beanMethod() {
                            return new MyType();
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
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean("foo")
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean("bar")
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void explicitNameNameParam() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(name = "foo")
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(name = "bar")
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void explicitNameMultiNameParam() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(name = { "foo", "somethingElse" })
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean(name = { "bar", "somethingElse" })
                        public MyType beanMethod() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void qualifierName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        @Qualifier("foo")
                        public MyType myType() {
                            return new MyType();
                        }
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        @Qualifier("bar")
                        public MyType myType() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongQualifierName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        @Qualifier("fooz")
                        public MyType myType() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongBeanName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean({ "fooz" })
                        public MyType myType() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongImpliedName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        public MyType myType() {
                            return new MyType();
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongType() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean
                        public String foo() {
                            return "";
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongTypeWithExplicitName() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Bean;
                    import sample.MyType;
                    
                    class A {
                        @Bean("foo")
                        public String myBean() {
                            return "";
                        }
                    }
                    """
                  )
                );
            }

            @Test
            void wrongAnnotation() {
                rewriteRun(
                  java(
                    """
                    package sample;
                    
                    import sample.MyAnnotation;
                    import sample.MyType;
                    
                    class A {
                        @MyAnnotation
                        public String foo() {
                            return "";
                        }
                    }
                    """
                  )
                );
            }
        }

        @Nested
        class BeanClasses {

            @Test
            void impliedName() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "foo", "bar")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration
                    class Foo {
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration
                    class Bar {
                    }
                    """
                  )
                );
            }

            @Test
            void explicitName() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "foo", "bar")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration("foo")
                    class Foo {
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration("bar")
                    class Foo {
                    }
                    """
                  )
                );
            }

            @Test
            void qualifierName() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "fooz", "barz")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration
                    @Qualifier("fooz")
                    class Foo {
                    }
                    """, """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration
                    @Qualifier("barz")
                    class Foo {
                    }
                    """
                  )
                );
            }

            @Test
            void wrongQualifierName() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "foo", "bar")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration
                    @Qualifier("fooz")
                    class Foo {
                    }
                    """
                  )
                );
            }

            @Test
            void wrongType() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "foo", "bar")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import org.springframework.context.annotation.Configuration;
                    import sample.MyType;
                    
                    @Configuration("foo")
                    class A {
                    }
                    """
                  )
                );
            }

            @Test
            void wrongAnnotation() {
                rewriteRun(
                  spec -> spec.recipe(new RenameBean("sample.Foo", "foo", "bar")),
                  java(
                    """
                    package sample;
                    
                    import org.springframework.beans.factory.annotation.Qualifier;
                    import sample.MyAnnotation;
                    import sample.MyType;
                    
                    @MyAnnotation("foo")
                    class A {
                    }
                    """
                  )
                );
            }
        }
    }

    @Nested
    class Usages {

        @Test
        void parameterUsage() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Bean
                    public String myBean(@Qualifier("foo") MyType myType) {
                        return "";
                    }
                }
                """, """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Bean
                    public String myBean(@Qualifier("bar") MyType myType) {
                        return "";
                    }
                }
                """
              )
            );
        }

        @Test
        void parameterWrongType() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Bean
                    public String myBean(@Qualifier("foo") String myString) {
                        return "";
                    }
                }
                """
              )
            );
        }

        @Test
        void parameterWrongName() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Bean
                    public String myBean(@Qualifier("fooz") MyType myType) {
                        return "";
                    }
                }
                """
              )
            );
        }

        @Test
        void parameterNoName() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Bean
                    public String myBean(MyType myType) {
                        return "";
                    }
                }
                """
              )
            );
        }

        @Test
        void fieldUsage() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Qualifier("foo")
                    private MyType myType;
                }
                """, """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Qualifier("bar")
                    private MyType myType;
                }
                """
              )
            );
        }

        @Test
        void fieldWrongType() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Qualifier("foo")
                    private String myString;
                }
                """
              )
            );
        }

        @Test
        void fieldWrongName() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    @Qualifier("fooz")
                    private MyType myTye;
                }
                """
              )
            );
        }

        @Test
        void fieldNoName() {
            rewriteRun(
              java(
                """
                package sample;
                
                import org.springframework.context.annotation.Configuration;
                import sample.MyType;
                
                @Configuration
                class A {
                    private MyType myTye;
                }
                """
              )
            );
        }
    }
}