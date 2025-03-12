package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class MigrateMethodAnnotatedByBatchAPITest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-batch-core-4", "spring-batch-infrastructure", "spring-beans"))
          .recipe(new MigrateMethodAnnotatedByBatchAPI());
    }

    @DocumentExample
    @Test
    void fixMethodArguments() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
            package test;
            import java.util.List;
            import org.springframework.batch.core.annotation.BeforeWrite;

            public class ProfileUpdateWriter {

                @BeforeWrite
                public void write(List<? extends List<T>> items) throws Exception {
                    for (List<T> subList : items) {
                    }
                }

            }
              """,
            """
            package test;
            import java.util.List;
            import org.springframework.batch.core.annotation.BeforeWrite;
            import org.springframework.batch.item.Chunk;

            public class ProfileUpdateWriter {

                @BeforeWrite
                public void write(Chunk<? extends List<T>> _chunk) throws Exception {
                    List<? extends List<T>> items = _chunk.getItems();
                    for (List<T> subList : items) {
                    }
                }

            }
              """
          )
        );
    }

}
