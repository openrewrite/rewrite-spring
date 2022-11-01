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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateWebMvcConfigurerAdapterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("spring-webmvc", "spring-core", "spring-web"))
          .recipe(new MigrateWebMvcConfigurerAdapter());
    }

    @Test
    void transformSimple() {
        rewriteRun(
          //language=java
          java("""
            package a.b.c;
            
            import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
            
            public class CustomMvcConfigurer extends WebMvcConfigurerAdapter {
                private final String someArg;
                public CustomMvcConfigurer(String someArg) {
                    super();
                    this.someArg = someArg;
                }
            }
            ""","""
            package a.b.c;
            
            import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
            
            public class CustomMvcConfigurer implements WebMvcConfigurer {
                private final String someArg;
                public CustomMvcConfigurer(String someArg) {
                    this.someArg = someArg;
                }
            }
            """)
        );
    }
}
