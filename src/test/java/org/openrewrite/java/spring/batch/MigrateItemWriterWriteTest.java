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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantThrows")
class MigrateItemWriterWriteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateItemWriterWrite())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.10", "spring-beans-4.3.30.RELEASE"));
    }

    @DocumentExample
    @Test
    void replaceItemWriterWriteMethod() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      for (final T item : items) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      for (final T item : items) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceExtendedItemWriterWriteMethod() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import org.springframework.batch.item.database.JdbcBatchItemWriter;

              public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {

                  public void write(List<? extends String> a) throws Exception {
                      super.write(a);
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.database.JdbcBatchItemWriter;

              public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {

                  @Override
                  public void write(Chunk<? extends String> a) throws Exception {
                      super.write(a);
                  }
              }
              """
          )
        );
    }

    @Test
    void abstractClass() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public abstract class AbstractItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public abstract void write(final List<? extends T> items) throws Exception;
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public abstract class AbstractItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public abstract void write(final Chunk<? extends T> items) throws Exception;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void replaceListMethodInvocations() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      if (items.size() >= 3) {
                          T item = items.get(2);
                          System.out.println(item.toString());
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      if (items.getItems().size() >= 3) {
                          T item = items.getItems().get(2);
                          System.out.println(item.toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void updateListInitialization() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      List<? extends T> other = items;
                      if (!other.isEmpty()) {
                          T item = other.get(0);
                          System.out.println(item);
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      List<? extends T> other = items.getItems();
                      if (!other.isEmpty()) {
                          T item = other.get(0);
                          System.out.println(item);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void updateListAssignment() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      List<? extends T> other;
                      other = items;
                      if (!other.isEmpty()) {
                          T item = other.get(0);
                          System.out.println(item);
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      List<? extends T> other;
                      other = items.getItems();
                      if (!other.isEmpty()) {
                          T item = other.get(0);
                          System.out.println(item);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void updateListMethodParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {
                  private void method(List<? extends T> items) {
                      for (T item : items) {
                          System.out.println(item.toString());
                      }
                  }

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      method(items);
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {
                  private void method(List<? extends T> items) {
                      for (T item : items) {
                          System.out.println(item.toString());
                      }
                  }

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      method(items.getItems());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void doesNotUpdateIterableAssignment() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      Iterable<? extends T> other = items;
                      for (T item : other) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      Iterable<? extends T> other = items;
                      for (T item : other) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeIterableMethodParameter() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {
                  private void method(Iterable<? extends T> items) {
                      for (T item : items) {
                          System.out.println(item.toString());
                      }
                  }

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      method(items);
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {
                  private void method(Iterable<? extends T> items) {
                      for (T item : items) {
                          System.out.println(item.toString());
                      }
                  }

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      method(items);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/411")
    @Test
    void doesNotChangeOtherLists() {
        // language=java
        rewriteRun(
          java(
            """
              import org.springframework.batch.item.ItemWriter;

              import java.util.ArrayList;
              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      List<? extends T> other = new ArrayList<>();
                      for (T item : other) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              import java.util.ArrayList;
              import java.util.List;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      List<? extends T> other = new ArrayList<>();
                      for (T item : other) {
                          System.out.println(item.toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void itemsStream() {
        // language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final List<? extends T> items) throws Exception {
                      items.stream().forEach(item -> System.out.println(item.toString()));
                  }
              }
              """,
            """
              import org.springframework.batch.item.Chunk;
              import org.springframework.batch.item.ItemWriter;

              public class ConsoleItemWriter<T> implements ItemWriter<T> {

                  @Override
                  public void write(final Chunk<? extends T> items) throws Exception {
                      items.getItems().stream().forEach(item -> System.out.println(item.toString()));
                  }
              }
              """
          )
        );
    }

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
    void migratesImplementerWithDelegatedWriteCalls() {
        // language=java
        rewriteRun(
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
    void doesNotWrapPassthroughOfMigratedParameter() {
        // language=java
        rewriteRun(
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
