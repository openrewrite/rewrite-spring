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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateItemWriterWriteTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new MigrateItemWriterWrite())
				.parser(JavaParser.fromJavaVersion().classpath("spring-batch-core", "spring-batch-infrastructure"));
	}

	@Test
	void doNotChangeCurrentApi() {
		// language=java
		rewriteRun(java("""
				import org.springframework.batch.item.Chunk;
				import org.springframework.batch.item.ItemWriter;

				public class ConsoleItemWriter<T> implements ItemWriter<T> {

				    @Override
				    public void write(final Chunk<? extends T> chunk) throws Exception {
				        for (final T item : chunk) {
				            System.out.println(item.toString());
				        }
				    }

				}
				"""));
	}

	@Disabled("until fixed!")
	@Test
	void replaceItemWriterWriteMethod() {
		// language=java
		rewriteRun(java("""
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
				""", """
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
				"""));
	}

	@Disabled("until fixed!")
	@Test
	void replaceExtendedItemWriterWriteMethod() {
		// language=java
		rewriteRun(java("""
				import java.util.List;
				import org.springframework.batch.item.database.JdbcBatchItemWriter;

				public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {

				    @Override
				    public void write(List<? extends String> items) throws Exception {
				        super.write(items);
				    }
				}
				""", """
				import org.springframework.batch.item.Chunk;
				import org.springframework.batch.item.database.JdbcBatchItemWriter;

				public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {

				    @Override
				    public void write(Chunk<? extends String> items) throws Exception {
				        super.write(items);
				    }
				}
				"""));
	}
}
