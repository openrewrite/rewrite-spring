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
package org.openrewrite.java.spring.boot3;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class UseReactorContextPropagationTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new UseReactorContextPropagation())
		  .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
	}

	@DocumentExample
	@Test
	void replaceMethodCallWithProperty() {
		rewriteRun(
		  //language=java
		  spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot3.UseReactorContextPropagation"),
		  java(
			"""
		 	import reactor.core.publisher.Hooks;
		 	import org.springframework.boot.SpringApplication;
		 	import org.springframework.boot.autoconfigure.SpringBootApplication;

		 	@SpringBootApplication
		 	public class MyApp{
		 		public static void main(String[] args) {
		 			Hooks.enableAutomaticContextPropagation();
		 			SpringApplication.run(MyApplication.class, args);
				}
			}
		 	""",
			"""
		  	import reactor.core.publisher.Hooks;
		 	import org.springframework.boot.SpringApplication;
		 	import org.springframework.boot.autoconfigure.SpringBootApplication;

		  	@SpringBootApplication
		  	public class MyApp {
		  		public static void main(String[] args) {
		  			SpringApplication.run(MyApplication.class, args);
		  		}
			}
		  	"""
		  ),
		  properties(
			"",
			"spring.reactor.context-propagation=true"
		  )
		);
	}
}
