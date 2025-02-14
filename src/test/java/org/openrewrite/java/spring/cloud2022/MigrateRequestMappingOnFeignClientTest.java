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
package org.openrewrite.java.spring.cloud2022;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateRequestMappingOnFeignClientTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateRequestMappingOnFeignClient())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-web",
            "spring-cloud-openfeign-core"));
    }

    @Test
    @DocumentExample
    void migrateRequestMappingAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping(path = "/posts")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(path = "/posts", name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingWithDefaultAttributeName() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping("/posts")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(path = "/posts", name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingWithDefaultAttributeNameAndArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping({"/posts"})
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(path = "/posts", name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingWithValueAttributeName() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping(value = "/posts")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.GetMapping;

              @FeignClient(path = "/posts", name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @GetMapping(value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void withRequestMappingAnnotationOnMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping(path = "/posts")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(path = "/posts", name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void withNonRequestMappingOnFeignClientButWithOneOnMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingAnnotationWithMultipleAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping(headers = "X-My-Header=MyValue", path = "/posts")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingAnnotationWithNonAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """,
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void requestMappingAnnotationWithOnlyHeadersAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080")
              @RequestMapping(headers = "X-My-Header=MyValue")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

    @Test
    void feignClientAnnotationAlreadyHasPathAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.cloud.openfeign.FeignClient;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;
              import org.springframework.web.bind.annotation.PathVariable;

              @FeignClient(name = "myService", url = "http://localhost:8080", path = "/api/v1")
              @RequestMapping(path = "/posts")
              public interface MyServiceClient {

                  @RequestMapping(method = RequestMethod.GET, value = "/{postId}", produces = "application/json")
                  String getPostById(@PathVariable("postId") Long postId);
              }
              """
          )
        );
    }

}
