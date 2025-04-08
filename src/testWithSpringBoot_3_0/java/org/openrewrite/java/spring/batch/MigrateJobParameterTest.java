/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateJobParameterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-batch-core-4.3.+",
            "spring-batch-infrastructure-4.3.+",
            "spring-beans-4.3.+"))
          .recipe(new MigrateJobParameter());
    }

    @DocumentExample
    @Test
    void addClassArguments() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;

              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      JobParameters jobParameters = new JobParameters(Map.of(
                              "inputFile", new JobParameter("TEST_INPUT_FILE"),
                              "emailId", new JobParameter(new Date()),
                              "pgpKey", new JobParameter(new Integer[]{1})
                      ));
                  }

              }
              """,
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;

              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      JobParameters jobParameters = new JobParameters(Map.of(
                              "inputFile", new JobParameter<>("TEST_INPUT_FILE", String.class),
                              "emailId", new JobParameter<>(new Date(), Date.class),
                              "pgpKey", new JobParameter<>(new Integer[]{1}, java.lang.Integer[].class)
                      ));
                  }

              }
              """
          )
        );
    }

    @Test
    void addStringClassArguments() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;
              import java.util.HashMap;
              import java.util.UUID;
              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      String from = UUID.randomUUID().toString();
                      Map<String, JobParameter> parameters = new HashMap<>();
                      parameters.put("sellerId", new JobParameter(from));
                      parameters.put("smtpHost", new JobParameter("localhost"));

                      JobParameters jobParameters = new JobParameters(parameters);
                  }

              }
              """,
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;
              import java.util.HashMap;
              import java.util.UUID;
              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      String from = UUID.randomUUID().toString();
                      Map<String,JobParameter<?>> parameters = new HashMap<>();
                      parameters.put("sellerId", new JobParameter<>(from, String.class));
                      parameters.put("smtpHost", new JobParameter<>("localhost", String.class));

                      JobParameters jobParameters = new JobParameters(parameters);
                  }

              }
              """
          )
        );
    }

    @Test
    void test3() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.HashMap;
              import java.util.Map;
              import java.util.UUID;
              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      final HashMap<String, JobParameter> paramMap = new HashMap<String, JobParameter>() {{
                                  put("Target", new JobParameter("JOB_NAME"));
                      }};
                  }

              }
              """,
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.HashMap;
              import java.util.Map;
              import java.util.UUID;
              public class Foo {

                  @Test
                  public void foo1() throws Exception {
                      final Map<String,JobParameter<?>> paramMap = new HashMap<>() {{
                                  put("Target", new JobParameter<>("JOB_NAME", String.class));
                      }};
                  }

              }
              """
          )
        );
    }

    @Test
    void test4() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;
              import java.util.HashMap;
              import java.util.UUID;
              public class Foo {

                  public Map<String, JobParameter> foo1(String carrier, String dirLocation) {
                      Map<String, JobParameter> parameters = new HashMap<>();
                      parameters.put("ARG_CARRIER", new JobParameter(carrier));
                      parameters.put("ARG_DIRECTORY_LOCATION", new JobParameter(dirLocation));
                      return parameters;
                  }

              }
              """,
            """
              import org.springframework.batch.core.JobParameter;
              import org.springframework.batch.core.JobParameters;
              import java.util.Date;
              import java.util.Map;
              import java.util.HashMap;
              import java.util.UUID;
              public class Foo {

                  public Map<String,JobParameter<?>> foo1(String carrier, String dirLocation) {
                      Map<String,JobParameter<?>> parameters = new HashMap<>();
                      parameters.put("ARG_CARRIER", new JobParameter<>(carrier, String.class));
                      parameters.put("ARG_DIRECTORY_LOCATION", new JobParameter<>(dirLocation, String.class));
                      return parameters;
                  }

              }
              """
          )
        );
    }
}
