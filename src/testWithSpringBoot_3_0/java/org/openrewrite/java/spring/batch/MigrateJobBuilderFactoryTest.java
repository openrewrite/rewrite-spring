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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

class MigrateJobBuilderFactoryTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new MigrateJobBuilderFactory())
				.parser(JavaParser.fromJavaVersion().classpath("spring-batch-core", "spring-beans", "spring-context"));
	}

	@Test
	void doNotChangeCurrentApi() {
		// language=java
		rewriteRun(java("""
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.job.builder.JobBuilder;
				import org.springframework.batch.core.repository.JobRepository;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Bean
					Job myJob(Step step, JobRepository jobRepository) {
						return new JobBuilder("myJob", jobRepository)
							.start(step)
							.build();
					}
				}
				"""));
	}

	@Test
	void replaceAutowiredJobBuilderFactory() {
		// language=java
		rewriteRun(spec -> spec.typeValidationOptions(new TypeValidation(true, false, false, true)), 
				java("""
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Autowired
					private JobBuilderFactory jobBuilderFactory;

					@Bean
					Job myJob(Step step) {
						return this.jobBuilderFactory.get("myJob")
							.start(step)
							.build();
					}
				}
				""", """
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.job.builder.JobBuilder;
				import org.springframework.batch.core.repository.JobRepository;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Bean
					Job myJob(Step step, JobRepository jobRepository) {
						return new JobBuilder("myJob", jobRepository)
							.start(step)
							.build();
					}
				}
				"""));
	}

	@Test
	void replaceJobBuilderFactory() {
		// language=java
		rewriteRun(spec -> spec.typeValidationOptions(new TypeValidation(true, false, false, true)), 
				java("""
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Bean
					Job myJob(JobBuilderFactory jobBuilderFactory, Step step) {
						return jobBuilderFactory.get("myJob")
							.start(step)
							.build();
					}
				}
				""", """
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.job.builder.JobBuilder;
				import org.springframework.batch.core.repository.JobRepository;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Bean
					Job myJob(Step step, JobRepository jobRepository) {
						return new JobBuilder("myJob", jobRepository)
							.start(step)
							.build();
					}
				}
				"""));
	}

	@Test
	void replaceJobBuilderFactoryInsideConstructor() {
		// language=java
		rewriteRun(spec -> spec.typeValidationOptions(new TypeValidation(true, false, false, true)), 
				java("""
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					private JobBuilderFactory jobBuilderFactory;

					public MyJobConfig(JobBuilderFactory jobBuilderFactory) {
						this.jobBuilderFactory = jobBuilderFactory;
					}

					@Bean
					Job myJob(JobBuilderFactory jobBuilderFactory, Step step) {
						return jobBuilderFactory.get("myJob")
							.start(step)
							.build();
					}
				}
				""", """
				import org.springframework.batch.core.Job;
				import org.springframework.batch.core.Step;
				import org.springframework.batch.core.job.builder.JobBuilder;
				import org.springframework.batch.core.repository.JobRepository;
				import org.springframework.context.annotation.Bean;

				public class MyJobConfig {

					@Bean
					Job myJob(Step step, JobRepository jobRepository) {
						return new JobBuilder("myJob", jobRepository)
							.start(step)
							.build();
					}
				}
				"""));
	}

}
