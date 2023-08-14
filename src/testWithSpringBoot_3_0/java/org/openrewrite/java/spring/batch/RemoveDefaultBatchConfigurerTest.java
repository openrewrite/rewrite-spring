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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantThrows")
class RemoveDefaultBatchConfigurerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDefaultBatchConfigurer())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-batch-core-4.3.+"));
    }

    @DocumentExample
    @Test
    void removeSetDataSourceWithCommentOnly() {
        // language=java
        rewriteRun(
          java("""
            import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
            class Foo extends DefaultBatchConfigurer {
                @Override
                public void setDataSource(javax.sql.DataSource dataSource) {
                    // Datasource ignored; this method and comment should be removed
                }
            }
            """, """
            class Foo {
            }
            """)
        );
    }

    @Test
    void removeSetDatasourceWithSuperCallOnly() {
        // language=java
        rewriteRun(
          java("""
            import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
            class Foo extends DefaultBatchConfigurer {
                @Override
                public void setDataSource(javax.sql.DataSource dataSource) {
                    super.setDataSource(dataSource);
                }
            }
            """, """
            class Foo {
            }
            """)
        );
    }

    @Test
    void retainSetDataSourceWithAdditionalStatements() {
        // language=java
        rewriteRun(
          java("""
            import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
            class Foo extends DefaultBatchConfigurer {
                @Override
                public void setDataSource(javax.sql.DataSource dataSource) {
                    super.setDataSource(dataSource);
                    System.out.println("Additional statements should ensure method is not removed");
                }
            }
            """, """
            class Foo {
                /*~~(TODO Used to override a DefaultBatchConfigurer method; reconsider if still needed)~~>*/
                public void setDataSource(javax.sql.DataSource dataSource) {
                    System.out.println("Additional statements should ensure method is not removed");
                }
            }
            """)
        );
    }

    @Test
    @Disabled("Not retaining additional interface overrides for now")
    void retainInterfaceOverrides() {
        // language=java
        rewriteRun(
          java("""
            package bar;
            public interface Bar {
                void baz();
            }
            """),
          java("""
            import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
            class Foo extends DefaultBatchConfigurer implements bar.Bar {
                @Override
                public void setDataSource(javax.sql.DataSource dataSource) {
                    // Datasource ignored; this method and comment should be removed
                }
                
                @Override
                public void baz() {
                    super.baz();
                }
            }
            """, """
            class Foo implements bar.Bar {
                @Override
                public void baz() {
                    super.baz();
                }
            }
            """)
        );
    }
}
