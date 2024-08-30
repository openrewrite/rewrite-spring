/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RefactorSimpleMongoDbFactoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.data.UpgradeSpringData_2_3")
          .parser(JavaParser.fromJavaVersion().classpath("spring-data-mongodb", "mongo-java-driver"));
    }

    @DocumentExample
    @Test
    void constructorWithAttribute() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.mongodb.MongoDbFactory;
              import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
              import com.mongodb.MongoClientURI;

              class Test {
                  private MongoDbFactory factory;
                  private SimpleMongoDbFactory factory2;

                  public void setupUri(String uri) {
                      factory2 = new SimpleMongoDbFactory(new MongoClientURI(uri));
                  }
              }
              """,
            """
              import org.springframework.data.mongodb.MongoDatabaseFactory;
              import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

              class Test {
                  private MongoDatabaseFactory factory;
                  private SimpleMongoClientDatabaseFactory factory2;

                  public void setupUri(String uri) {
                      factory2 = new SimpleMongoClientDatabaseFactory(uri);
                  }
              }
              """
          )
        );
    }
}
