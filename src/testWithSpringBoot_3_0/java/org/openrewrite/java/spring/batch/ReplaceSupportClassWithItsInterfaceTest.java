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

class ReplaceSupportClassWithItsInterfaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-batch", "spring-boot", "spring-beans", "spring-core",
                "spring-context"));
    }

    @Test
    void replaceChunkListenerSupport() {

        rewriteRun(
                spec -> spec.recipe(new ReplaceSupportClassWithItsInterface(
                        "org.springframework.batch.core.listener.ChunkListenerSupport",
                        "org.springframework.batch.core.ChunkListener")),
                // language=java
                java("""
                        import org.springframework.batch.core.listener.ChunkListenerSupport;

                        public class MyClass extends ChunkListenerSupport {

                        }
                        """, """
                        import org.springframework.batch.core.ChunkListener;

                        public class MyClass implements ChunkListener {

                        }
                        """));
    }

    @Test
    void replaceJobExecutionListenerSupport() {

        rewriteRun(
                spec -> spec.recipe(new ReplaceSupportClassWithItsInterface(
                        "org.springframework.batch.core.listener.JobExecutionListenerSupport",
                        "org.springframework.batch.core.JobExecutionListener")),
                // language=java
                java("""
                        import org.springframework.batch.core.listener.JobExecutionListenerSupport;

                        public class MyClass extends JobExecutionListenerSupport {

                        }
                        """, """
                        import org.springframework.batch.core.JobExecutionListener;

                        public class MyClass implements JobExecutionListener {

                        }
                        """));
    }

    @Test
    void replaceStepExecutionListenerSupport() {
        rewriteRun(
                spec -> spec.recipe(new ReplaceSupportClassWithItsInterface(
                        "org.springframework.batch.core.listener.StepExecutionListenerSupport",
                        "org.springframework.batch.core.StepExecutionListener")),
                // language=java
                java("""
                        import org.springframework.batch.core.listener.StepExecutionListenerSupport;

                        public class MyClass extends StepExecutionListenerSupport {

                        }
                        """, """
                        import org.springframework.batch.core.StepExecutionListener;

                        public class MyClass implements StepExecutionListener {

                        }
                        """));
    }

    @Test
    void replaceRepeatListenerSupport() {
        rewriteRun(
                spec -> spec.recipe(new ReplaceSupportClassWithItsInterface(
                        "org.springframework.batch.repeat.listener.RepeatListenerSupport",
                        "org.springframework.batch.repeat.RepeatListener")),
                // language=java
                java("""
                        import org.springframework.batch.repeat.listener.RepeatListenerSupport;

                        public class MyClass extends RepeatListenerSupport {

                        }
                        """, """
                        import org.springframework.batch.repeat.RepeatListener;

                        public class MyClass implements RepeatListener {

                        }
                        """));
    }

    @Test
    void replaceSkipListenerSupport() {
        rewriteRun(
                spec -> spec.recipe(new ReplaceSupportClassWithItsInterface(
                        "org.springframework.batch.core.listener.SkipListenerSupport",
                        "org.springframework.batch.core.SkipListener")),
                // language=java
                java("""
                        import org.springframework.batch.core.listener.SkipListenerSupport;

                        public class MyClass extends SkipListenerSupport {

                        }
                        """, """
                        import org.springframework.batch.core.SkipListener;

                        public class MyClass implements SkipListener {

                        }
                        """));
    }

}
