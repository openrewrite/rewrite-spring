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
package org.openrewrite.java.spring.boot2.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class EntityIdForRepositoryVisitorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new EntityIdForRepositoryVisitor<>()))
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans", "spring-data"));
    }

    @Test
    void invalidDomainId_string_number() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void noId() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            public class Employee {
                String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void noRepoBean() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.NoRepositoryBean;
              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              @NoRepositoryBean
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void invalidDomainId_string_long() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Long.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void invalidDomainId_integer_double() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id Integer id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Double.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.Integer')~~>*/Double.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void validDomainId_CharSequence_String() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = CharSequence.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void validDomainId_String_CharSequence() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id CharSequence id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = String.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void validDomainId_int_Integer() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id int id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Integer.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void inheritance_invalidDomainId_string_number_1() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.Repository;
              
              interface CustomerRepository extends Repository<Customer, Long>{}
              """,
            """
              package demo;

              import org.springframework.data.repository.Repository;
              
              interface CustomerRepository extends Repository<Customer, /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long>{}
              """
          )
        );
    }

    @Test
    void inheritance_invalidDomainId_string_number_2() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.Repository;
              
              interface CustomerRepository<T extends Customer, ID extends Long> extends Repository<T, ID>{}
              """,
            """
              package demo;

              import org.springframework.data.repository.Repository;
              
              interface CustomerRepository<T extends Customer, /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/ID extends Long> extends Repository<T, ID>{}
              """
          )
        );
    }

    @Test
    void inheritance_intermediateRepo_1() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyIntermediateRepository<T extends Customer, ID extends Number> extends Repository<T, ID>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyConcreteRepository extends MyIntermediateRepository<Customer, Long>{}
              """,
            """
              package demo;
              
              interface MyConcreteRepository extends MyIntermediateRepository<Customer, /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long>{}
              """
          )
        );
    }

    @Test
    void inheritance_intermediateRepo_2() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository1<T extends Customer> extends Repository<T, Long>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository1 extends MyOtherIntermediateRepository1<Customer>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository1 extends /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/MyOtherIntermediateRepository1<Customer>{}
              """
          )
        );
    }

    @Test
    void inheritance_intermediateRepo_3() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository2<ID extends Number> extends Repository<Customer, ID>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2</*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long>{}
              """
          )
        );
    }

    @Test
    void inheritance_extra_interface() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository2<ID extends Number, T> extends Repository<Customer, ID>, Iterator<T>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long, String>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2</*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long, String>{}
              """

          )
        );
    }

    @Test
    void inheritance_reversed_template_params_1() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository1<ID extends Number, T> extends Repository<T, ID>{}
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository2<ID, T> extends MyOtherIntermediateRepository1<ID, T>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long, Customer>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2</*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long, Customer>{}
              """
          )
        );
    }

    @Test
    void inheritance_reversed_template_params_2() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository1<ID, DOMAIN> extends Repository<DOMAIN, ID>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository1<Long, Customer>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository1</*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long, Customer>{}
              """
          )
        );
    }

    @Test
    void inheritance_advanced_1() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository2<S, T, ID, D> extends Repository<D, ID>, Iterator<T>, List<S>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long, String, Long, Customer>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long, String, /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long, Customer>{}
              """
          )
        );
    }

    @Test
    void inheritance_advanced_2() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Customer {
                @Id String id;
            }
            """
          ),
          java(
                """
            package demo;
            
            import org.springframework.data.repository.NoRepositoryBean;
            import org.springframework.data.repository.Repository;
            
            @NoRepositoryBean
            interface MyOtherIntermediateRepository1<S, T, ID, D> extends Repository<D, ID>, Iterator<T>, List<S>{}
            """
          ),
          java(
                """
            package demo;
            
            interface MyOtherIntermediateRepository2<S> extends MyOtherIntermediateRepository1<S, String, Long, Customer>{}
            """,
            """
            package demo;
              
            interface MyOtherIntermediateRepository2<S> extends MyOtherIntermediateRepository1<S, String, /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Long, Customer>{}
            """
          ),
          java(
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends MyOtherIntermediateRepository2<Long>{}
              """,
            """
              package demo;
              
              interface MyOtherConcreteRepository2 extends /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/MyOtherIntermediateRepository2<Long>{}
              """
          )
        );
    }

    @Test
    void invalid_DomainId_record() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public record Employee(@Id String id) {}
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void valid_DomainId_record() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public record Employee(@Id String id) {}
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = String.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void domain_type_with_inheritance_1() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Person {
                @Id String id;
            }
            """
          ),
          java(
            """
            package demo;
            
            public class Employee extends Person {
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void domain_type_with_inheritance_2() {
        //language=java
        rewriteRun(
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Person {
                @Id String id;
            }
            """
          ),
          java(
            """
            package demo;
            
            public class Employee extends Person {
                String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void id_field_invalid_DomainId_1() {
        //language=java
        rewriteRun(spec -> {
            spec.recipe(toRecipe(() -> new EntityIdForRepositoryVisitor<>(true)));
          },
          java(
            """
            package demo;
            
            public class Employee {
                String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }



    @Test
    void id_field_invalid_DomainId_2() {
        //language=java
        rewriteRun(spec -> {
              spec.recipe(toRecipe(() -> new EntityIdForRepositoryVisitor<>(true)));
          },
          java(
            """
            package demo;
            
            public class Person {
                String id;
            }
            """
          ),
          java(
            """
            package demo;
            
            public class Employee extends Person {
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~(Expected Domain Type ID is 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void id_field_no_validation() {
        //language=java
        rewriteRun(spec -> {
              spec.recipe(toRecipe(() -> new EntityIdForRepositoryVisitor<>(false)));
          },
          java(
            """
            package demo;
            
            public class Employee {
                String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

    @Test
    void customMarker() {
        //language=java
        rewriteRun(spec -> {
              spec.recipe(toRecipe(() -> new EntityIdForRepositoryVisitor<>() {
                  @Override
                  protected Marker createMarker(JavaType domainIdType) {
                      return new SearchResult(Tree.randomId(), "[Overridden marker]  DomainType ID must be '" + domainIdType + "'");
                  }
              }));
          },
          java(
            """
            package demo;
            
            import org.springframework.data.annotation.Id;
            
            public class Employee {
                @Id String id;
            }
            """
          ),
          java(
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = Number.class)
              interface EmployeeRepository {}
              """,
            """
              package demo;

              import org.springframework.data.repository.RepositoryDefinition;
              
              @RepositoryDefinition(domainClass = Employee.class, idClass = /*~~([Overridden marker]  DomainType ID must be 'java.lang.String')~~>*/Number.class)
              interface EmployeeRepository {}
              """
          )
        );
    }

}
