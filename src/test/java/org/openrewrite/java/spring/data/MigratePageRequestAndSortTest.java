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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class MigratePageRequestAndSortTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigratePageRequestAndSort())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-data-commons-1.13"));
    }

    @DocumentExample
    @Test
    void pageRequest() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.PageRequest;
              import org.springframework.data.domain.Pageable;
              import org.springframework.data.domain.Sort;

              class Pages {
                  Pageable first() {
                      return new PageRequest(0, 10);
                  }

                  Pageable sorted(Sort sort) {
                      return new PageRequest(0, 10, sort);
                  }

                  Pageable byName() {
                      return new PageRequest(0, 10, Sort.Direction.ASC, "lastName", "firstName");
                  }

                  Pageable nested() {
                      return new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "created"));
                  }
              }
              """,
            """
              import org.springframework.data.domain.PageRequest;
              import org.springframework.data.domain.Pageable;
              import org.springframework.data.domain.Sort;

              class Pages {
                  Pageable first() {
                      return PageRequest.of(0, 10);
                  }

                  Pageable sorted(Sort sort) {
                      return PageRequest.of(0, 10, sort);
                  }

                  Pageable byName() {
                      return PageRequest.of(0, 10, Sort.Direction.ASC, "lastName", "firstName");
                  }

                  Pageable nested() {
                      return PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "created"));
                  }
              }
              """
          )
        );
    }

    @Test
    void sort() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Direction;
              import org.springframework.data.domain.Sort.Order;
              import java.util.List;

              class Sorts {
                  Sort properties() {
                      return new Sort("lastName", "firstName");
                  }

                  Sort direction() {
                      return new Sort(Direction.DESC, "created", "name");
                  }

                  Sort orders(Order first, Order second) {
                      return new Sort(first, second);
                  }

                  Sort orderList(List<Order> orders) {
                      return new Sort(orders);
                  }
              }
              """,
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Direction;
              import org.springframework.data.domain.Sort.Order;
              import java.util.List;

              class Sorts {
                  Sort properties() {
                      return Sort.by("lastName", "firstName");
                  }

                  Sort direction() {
                      return Sort.by(Direction.DESC, "created", "name");
                  }

                  Sort orders(Order first, Order second) {
                      return Sort.by(first, second);
                  }

                  Sort orderList(List<Order> orders) {
                      return Sort.by(orders);
                  }
              }
              """
          )
        );
    }

    @Test
    void directionWithAListOfPropertiesHasNoSortByOfItsOwn() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.Sort;
              import java.util.Arrays;
              import java.util.List;

              class Sorts {
                  Sort variable(List<String> properties) {
                      return new Sort(Sort.Direction.ASC, properties);
                  }

                  Sort expression() {
                      return new Sort(Sort.Direction.ASC, Arrays.asList("lastName", "firstName"));
                  }

                  Sort multiline(List<String> properties) {
                      return new Sort(Sort.Direction.ASC,
                                      properties);
                  }
              }
              """,
            """
              import org.springframework.data.domain.Sort;
              import java.util.Arrays;
              import java.util.List;

              class Sorts {
                  Sort variable(List<String> properties) {
                      return Sort.by(Sort.Direction.ASC, properties.toArray(new String[0]));
                  }

                  Sort expression() {
                      return Sort.by(Sort.Direction.ASC, Arrays.asList("lastName", "firstName").toArray(new String[0]));
                  }

                  Sort multiline(List<String> properties) {
                      return Sort.by(Sort.Direction.ASC,
                                      properties.toArray(new String[0]));
                  }
              }
              """
          )
        );
    }

    @Test
    void listOfPropertiesCallsTheVarargsFactoryMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.Sort;

              import java.util.List;

              class Sorts {
                  Sort sort(List<String> properties) {
                      return new Sort(Sort.Direction.ASC, properties);
                  }
              }
              """,
            """
              import org.springframework.data.domain.Sort;

              import java.util.List;

              class Sorts {
                  Sort sort(List<String> properties) {
                      return Sort.by(Sort.Direction.ASC, properties.toArray(new String[0]));
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                    if ("by".equals(method.getSimpleName())) {
                        JavaType.Method type = method.getMethodType();
                        assertThat(type.getParameterTypes().get(1)).isInstanceOf(JavaType.Array.class);
                        assertThat(type.hasFlags(Flag.Varargs, Flag.Static)).isTrue();
                    }
                    return super.visitMethodInvocation(method, p);
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void varargsStayVarargs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.data.domain.Sort;

              class Sorts {
                  Sort sort = new Sort("name", "age");
              }
              """,
            """
              import org.springframework.data.domain.Sort;

              class Sorts {
                  Sort sort = Sort.by("name", "age");
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                    assertThat(method.getMethodType().hasFlags(Flag.Varargs, Flag.Static)).isTrue();
                    return super.visitMethodInvocation(method, p);
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void orderOfAPropertyAlone() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Order;

              class Orders {
                  Order imported() {
                      return new Order("name");
                  }

                  Sort.Order qualified() {
                      return new Sort.Order("name");
                  }

                  Order withDirection() {
                      return new Order(Sort.Direction.ASC, "name");
                  }
              }
              """,
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Order;

              class Orders {
                  Order imported() {
                      return Order.by("name");
                  }

                  Sort.Order qualified() {
                      return Sort.Order.by("name");
                  }

                  Order withDirection() {
                      return new Order(Sort.Direction.ASC, "name");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsTheFormattingOfTheArguments() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Order;

              class Sorts {
                  Sort sort = new Sort(
                      new Order(
                          "lastName"),
                      // then by first name
                      new Order("firstName")
                  );
              }
              """,
            """
              import org.springframework.data.domain.Sort;
              import org.springframework.data.domain.Sort.Order;

              class Sorts {
                  Sort sort = Sort.by(
                      Order.by(
                          "lastName"),
                      // then by first name
                      Order.by("firstName")
                  );
              }
              """
          )
        );
    }

    @Test
    void fullyQualifiedStaysFullyQualified() {
        rewriteRun(
          java(
            """
              class Pages {
                  Object page = new org.springframework.data.domain.PageRequest(0, 10);
              }
              """,
            """
              class Pages {
                  Object page = org.springframework.data.domain.PageRequest.of(0, 10);
              }
              """
          )
        );
    }

    @Test
    void subclassesKeepTheirConstructors() {
        rewriteRun(
          java(
            """
              import org.springframework.data.domain.PageRequest;
              import org.springframework.data.domain.Sort;

              class LimitedPageRequest extends PageRequest {
                  LimitedPageRequest(int page) {
                      super(page, 10);
                  }
              }

              class Pages {
                  PageRequest own = new LimitedPageRequest(1);
                  PageRequest anonymous = new PageRequest(0, 10) {
                  };
                  Sort anonymousSort = new Sort("name") {
                  };
              }
              """
          )
        );
    }

    @Test
    void alreadyOnSpringData2() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-data-commons-2.7")),
          java(
            """
              import org.springframework.data.domain.PageRequest;
              import org.springframework.data.domain.Pageable;
              import org.springframework.data.domain.Sort;

              class Pages {
                  Pageable page = PageRequest.of(0, 10, Sort.by("name"));
                  Sort.Order order = new Sort.Order(Sort.Direction.ASC, "name");
              }
              """
          )
        );
    }
}
