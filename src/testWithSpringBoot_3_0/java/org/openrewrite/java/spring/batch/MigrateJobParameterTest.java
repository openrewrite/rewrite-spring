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
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-batch-core", "spring-batch-infrastructure", "spring-beans"))
          .recipe(new MigrateJobParameter());
    }

    @DocumentExample
    @Test
    void test1() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;

            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
                    JobParameters jobParameters = new JobParameters(Map.of(
                            "inputFile", new JobParameter("TEST_INPUT_FILE"),
                            "emailId", new JobParameter(new Date()),
                            "pgpKey", new JobParameter(new Integer[]{1})
                    ));
                }

            }
              """,
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;

            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
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



    @DocumentExample
    @Test
    void test2() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;
            import java.util.HashMap;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
                    String from = UUID.randomUUID().toString();
                    Map<String, JobParameter> parameters = new HashMap<>();
                    parameters.put("sellerId", new JobParameter(from));
                    parameters.put("smtpHost", new JobParameter("localhost"));

                    JobParameters jobParameters = new JobParameters(parameters);
                }

            }
              """,
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;
            import java.util.HashMap;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
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


    @DocumentExample
    @Test
    void test3() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.HashMap;
            import java.util.Map;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
                    final HashMap<String, JobParameter> paramMap = new HashMap<String, JobParameter>() {{
                                put("Target", new JobParameter("JOB_NAME"));
                    }};
                }

            }
              """,
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.HashMap;
            import java.util.Map;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                @Test
                public void shouldUpdateMemberStatusWhenDecisionCodeIsA() throws Exception {
                    final Map<String,JobParameter<?>> paramMap = new HashMap<>() {{
                                put("Target", new JobParameter<>("JOB_NAME", String.class));
                    }};
                }

            }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void test4() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;
            import java.util.HashMap;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                public Map<String, JobParameter> getParameters(String carrier, String dirLocation) {
                    Map<String, JobParameter> parameters = new HashMap<>();
                    parameters.put("ARG_CARRIER", new JobParameter(carrier));
                    parameters.put("ARG_DIRECTORY_LOCATION", new JobParameter(dirLocation));
                    return parameters;
                }

            }
              """,
            """
            package test;
            import org.springframework.batch.core.JobParameter;
            import org.springframework.batch.core.JobParameters;
            import java.util.Date;
            import java.util.Map;
            import java.util.HashMap;
            import java.util.UUID;
            public class PointsReconFileWriterTest {

                public Map<String,JobParameter<?>> getParameters(String carrier, String dirLocation) {
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
