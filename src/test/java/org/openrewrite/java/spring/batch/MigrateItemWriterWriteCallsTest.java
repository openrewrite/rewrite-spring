/*
 * Copyright 2024 the original author or authors.
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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantThrows")
class MigrateItemWriterWriteCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateItemWriterWriteCalls())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.10", "spring-beans-4.3.30.RELEASE"));
    }

    @DocumentExample
    @Test
    void wrapsListPassedToWriter() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              class Caller {
                  private final ItemWriter<String> writer;

                  Caller(ItemWriter<String> writer) {
                      this.writer = writer;
                  }

                  void send(List<String> items) throws Exception {
                      writer.write(items);
                  }
              }
              """,
            """
              import java.util.List;

              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              class Caller {
                  private final ItemWriter<String> writer;

                  Caller(ItemWriter<String> writer) {
                      this.writer = writer;
                  }

                  void send(List<String> items) throws Exception {
                      writer.write(new Chunk<>(items));
                  }
              }
              """
          )
        );
    }

    @Test
    void wrapsListLocalVariableFromForLoop() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              class Partitioner {
                  private final ItemWriter<String> victorFileWriter;

                  Partitioner(ItemWriter<String> victorFileWriter) {
                      this.victorFileWriter = victorFileWriter;
                  }

                  void writeAll(List<List<String>> partitions) throws Exception {
                      for (List<String> list : partitions) {
                          this.victorFileWriter.write(list);
                      }
                  }
              }
              """,
            """
              import java.util.List;

              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              class Partitioner {
                  private final ItemWriter<String> victorFileWriter;

                  Partitioner(ItemWriter<String> victorFileWriter) {
                      this.victorFileWriter = victorFileWriter;
                  }

                  void writeAll(List<List<String>> partitions) throws Exception {
                      for (List<String> list : partitions) {
                          this.victorFileWriter.write(new Chunk<>(list));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void wrapsArrayListSubtype() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.ArrayList;
              import org.springframework.batch.item.ItemWriter;

              class Caller {
                  private final ItemWriter<String> writer;

                  Caller(ItemWriter<String> writer) {
                      this.writer = writer;
                  }

                  void send() throws Exception {
                      ArrayList<String> list = new ArrayList<>();
                      writer.write(list);
                  }
              }
              """,
            """
              import java.util.ArrayList;

              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              class Caller {
                  private final ItemWriter<String> writer;

                  Caller(ItemWriter<String> writer) {
                      this.writer = writer;
                  }

                  void send() throws Exception {
                      ArrayList<String> list = new ArrayList<>();
                      writer.write(new Chunk<>(list));
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotWrapWhenReceiverIsNotItemWriter() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              class NotAWriter {
                  void write(List<String> items) {
                  }
              }

              class Caller {
                  private final NotAWriter writer = new NotAWriter();

                  void send(List<String> items) {
                      writer.write(items);
                  }
              }
              """
          )
        );
    }

    @Test
    void combinedRecipesProduceFullyMigratedCustomerCode() {
        // language=java
        rewriteRun(
          spec -> spec.recipes(new MigrateItemWriterWrite(), new MigrateItemWriterWriteCalls()),
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public class PartitionedItemWriter<T> implements ItemWriter<T> {

                  private final ItemWriter<T> victorFileWriter;

                  public PartitionedItemWriter(ItemWriter<T> victorFileWriter) {
                      this.victorFileWriter = victorFileWriter;
                  }

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      for (List<? extends T> list : partition(items)) {
                          this.victorFileWriter.write(list);
                      }
                  }

                  private List<List<? extends T>> partition(List<? extends T> items) {
                      return java.util.Collections.singletonList(items);
                  }
              }
              """,
            """
              import java.util.List;

              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class PartitionedItemWriter<T> implements ItemWriter<T> {

                  private final ItemWriter<T> victorFileWriter;

                  public PartitionedItemWriter(ItemWriter<T> victorFileWriter) {
                      this.victorFileWriter = victorFileWriter;
                  }

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      for (List<? extends T> list : partition(items.getItems())) {
                          this.victorFileWriter.write(new Chunk<>(list));
                      }
                  }

                  private List<List<? extends T>> partition(List<? extends T> items) {
                      return java.util.Collections.singletonList(items);
                  }
              }
              """
          )
        );
    }

    @Test
    void combinedRecipesDoNotDoubleWrapPassthrough() {
        // language=java
        rewriteRun(
          spec -> spec.recipes(new MigrateItemWriterWrite(), new MigrateItemWriterWriteCalls()),
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public class DelegatingItemWriter<T> implements ItemWriter<T> {

                  private final ItemWriter<T> delegate;

                  public DelegatingItemWriter(ItemWriter<T> delegate) {
                      this.delegate = delegate;
                  }

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      delegate.write(items);
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class DelegatingItemWriter<T> implements ItemWriter<T> {

                  private final ItemWriter<T> delegate;

                  public DelegatingItemWriter(ItemWriter<T> delegate) {
                      this.delegate = delegate;
                  }

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      delegate.write(items);
                  }
              }
              """
          )
        );
    }
}
