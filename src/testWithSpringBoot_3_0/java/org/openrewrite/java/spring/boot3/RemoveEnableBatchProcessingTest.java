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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveEnableBatchProcessingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveEnableBatchProcessing())
          .parser(JavaParser.fromJavaVersion().classpath("spring-batch-core", "spring-boot"));
    }

    @Test
    void removeSpringBatchAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
                            
              @SpringBootApplication
              @EnableBatchProcessing
              public class Application {
              }
              """,
            """
              import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
                            
              @SpringBootApplication
              public class Application {
              }
              """
          )
        );
    }
}
