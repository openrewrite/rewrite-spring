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
package org.openrewrite.java.spring.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeSpringKafka40Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/spring-kafka-40.yml", "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_4_0")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-kafka-4.0",
            "spring-context-7",
            "spring-core-7",
            "jackson-core-2.20"
          ));
    }

    @DocumentExample
    @Test
    void migrateJackson2ToJackson3() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
              import org.springframework.kafka.support.converter.JsonMessageConverter;
              import org.springframework.kafka.support.converter.ProjectingMessageConverter;
              import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
              import org.springframework.kafka.support.serializer.JsonDeserializer;
              import org.springframework.kafka.support.serializer.JsonSerde;
              import org.springframework.kafka.support.serializer.JsonSerializer;

              class Test {
                  DefaultKafkaHeaderMapper mapper = new DefaultKafkaHeaderMapper();
                  JsonMessageConverter converter = new JsonMessageConverter();
                  ProjectingMessageConverter projectingConverter = new ProjectingMessageConverter();
                  DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
                  JsonDeserializer<String> deserializer = new JsonDeserializer<>();
                  JsonSerde<String> serde = new JsonSerde<>();
                  JsonSerializer<String> serializer = new JsonSerializer<>();
              }
              """,
            """
              import org.springframework.kafka.support.JsonKafkaHeaderMapper;
              import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
              import org.springframework.kafka.support.converter.JacksonProjectingMessageConverter;
              import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
              import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
              import org.springframework.kafka.support.serializer.JacksonJsonSerde;
              import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

              class Test {
                  JsonKafkaHeaderMapper mapper = new JsonKafkaHeaderMapper();
                  JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
                  JacksonProjectingMessageConverter projectingConverter = new JacksonProjectingMessageConverter();
                  DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
                  JacksonJsonDeserializer<String> deserializer = new JacksonJsonDeserializer<>();
                  JacksonJsonSerde<String> serde = new JacksonJsonSerde<>();
                  JacksonJsonSerializer<String> serializer = new JacksonJsonSerializer<>();
              }
              """
          )
        );
    }
}
