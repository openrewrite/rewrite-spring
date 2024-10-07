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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateWebMvcTagsToObservationConventionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateWebMvcTagsToObservationConvention())
          .parser(JavaParser.fromJavaVersion().classpath(
            "micrometer-core",
            "spring-boot",
            "spring-context",
            "spring-beans",
            "spring-web",
            "jakarta.servlet-api"));
    }

    @DocumentExample
    @Test
    void shouldMigrateWebMvcTagsProviderToDefaultServerRequestObservationConvention() {
        //language=java
        rewriteRun(
          java(
            """
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import jakarta.servlet.http.HttpServletRequest;
              import jakarta.servlet.http.HttpServletResponse;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTags;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
              
              class CustomWebMvcTagsProvider implements WebMvcTagsProvider {
              
                  @Override
                  public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                      Tags tags = Tags.of(WebMvcTags.method(request), WebMvcTags.uri(request, response), WebMvcTags.status(response), WebMvcTags.outcome(response));
              
                      String customHeader = request.getHeader("X-Custom-Header");
                      if (customHeader != null) {
                          tags = tags.and("custom.header", customHeader);
                      }
                      return tags;
                  }
              }
              """,
            """
              import io.micrometer.common.KeyValue;
              import io.micrometer.common.KeyValues;
              import jakarta.servlet.http.HttpServletRequest;
              import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
              import org.springframework.http.server.observation.ServerRequestObservationContext;
              
              class CustomWebMvcTagsProvider extends DefaultServerRequestObservationConvention {
              
                  @Override
                  public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                      HttpServletRequest request = context.getCarrier();
                      KeyValues values = super.getHighCardinalityKeyValues(context);
              
                      String customHeader = request.getHeader("X-Custom-Header");
                      if (customHeader != null) {
                          values.and(KeyValue.of("custom.header", customHeader));
                      }
                      return values;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldMigrateTags_Of() {
        //language=java
        rewriteRun(
          java(
            """
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import jakarta.servlet.http.HttpServletRequest;
              import jakarta.servlet.http.HttpServletResponse;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTags;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
              
              class CustomWebMvcTagsProvider implements WebMvcTagsProvider {
              
                  Tags staticTags = Tags.of("a", "b", "c", "d");
                  Tag staticTag = Tag.of("a", "b");
              
                  @Override
                  public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                      Tags tags = Tags.of(WebMvcTags.method(request), WebMvcTags.uri(request, response), WebMvcTags.status(response), WebMvcTags.outcome(response));
                      tags = Tags.of("a", "b");
                      tags = Tags.of("a", "b", "c", "d");
                      tags = Tags.of(Tag.of("a", "b"), staticTag);
                      tags = Tags.of(staticTags);
                      return tags;
                  }
              }
              """,
            """
              import io.micrometer.common.KeyValue;
              import io.micrometer.common.KeyValues;
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
              import org.springframework.http.server.observation.ServerRequestObservationContext;
              
              class CustomWebMvcTagsProvider extends DefaultServerRequestObservationConvention {
              
                  Tags staticTags = Tags.of("a", "b", "c", "d");
                  Tag staticTag = Tag.of("a", "b");
              
                  @Override
                  public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                      KeyValues values = super.getHighCardinalityKeyValues(context);
                      values.and(KeyValue.of("a", "b"));
                      values.and(KeyValue.of("a", "b"), KeyValue.of("c", "d"));
                      values.and(KeyValue.of("a", "b"), KeyValue.of(staticTag.getKey(), staticTag.getValue()));
                      for (Tag tag : staticTags) {
                          values.and(KeyValue.of(tag.getKey(), tag.getValue()));
                      }
                      return values;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldMigrateTags_And() {
        //language=java
        rewriteRun(
          java(
            """
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import jakarta.servlet.http.HttpServletRequest;
              import jakarta.servlet.http.HttpServletResponse;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTags;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
              
              class CustomWebMvcTagsProvider implements WebMvcTagsProvider {
              
                  Tags staticTags = Tags.of("a", "b", "c", "d");
                  Tag staticTag = Tag.of("a", "b");
              
                  @Override
                  public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                      Tags tags = Tags.of(WebMvcTags.method(request), WebMvcTags.uri(request, response), WebMvcTags.status(response), WebMvcTags.outcome(response));
              
                      String customHeader = request.getHeader("X-Custom-Header");
                      if (customHeader != null) {
                          tags = tags.and("custom.header", customHeader);
                      }
                      if (response.getStatus() >= 400) {
                          tags = tags.and("error", "true");
                      }
                      tags = tags.and("a", "b", "c", "d");
                      tags = Tags.and(Tag.of("a", "b"), staticTag);
                      tags = tags.and(staticTags);
                      return tags;
                  }
              }
              """,
            """
              import io.micrometer.common.KeyValue;
              import io.micrometer.common.KeyValues;
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import jakarta.servlet.http.HttpServletRequest;
              import jakarta.servlet.http.HttpServletResponse;
              import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
              import org.springframework.http.server.observation.ServerRequestObservationContext;
              
              class CustomWebMvcTagsProvider extends DefaultServerRequestObservationConvention {
              
                  Tags staticTags = Tags.of("a", "b", "c", "d");
                  Tag staticTag = Tag.of("a", "b");
              
                  @Override
                  public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                      HttpServletRequest request = context.getCarrier();
                      HttpServletResponse response = context.getResponse();
                      KeyValues values = super.getHighCardinalityKeyValues(context);
              
                      String customHeader = request.getHeader("X-Custom-Header");
                      if (customHeader != null) {
                          values.and(KeyValue.of("custom.header", customHeader));
                      }
                      if (response.getStatus() >= 400) {
                          values.and(KeyValue.of("error", "true"));
                      }
                      values.and(KeyValue.of("a", "b"), KeyValue.of("c", "d"));
                      values.and(KeyValue.of("a", "b"), KeyValue.of(staticTag.getKey(), staticTag.getValue()));
                      for (Tag tag : staticTags) {
                          values.and(KeyValue.of(tag.getKey(), tag.getValue()));
                      }
                      return values;
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldMigrateReturnTags_Of() {
        //language=java
        rewriteRun(
          java(
            """
              import io.micrometer.core.instrument.Tag;
              import io.micrometer.core.instrument.Tags;
              import jakarta.servlet.http.HttpServletRequest;
              import jakarta.servlet.http.HttpServletResponse;
              import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
              
              class CustomWebMvcTagsProvider implements WebMvcTagsProvider {
              
                  @Override
                  public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                      return Tags.of(Tag.of("a", "b"));
                  }
              }
              """,
            """
              import io.micrometer.common.KeyValue;
              import io.micrometer.common.KeyValues;
              import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
              import org.springframework.http.server.observation.ServerRequestObservationContext;
              
              class CustomWebMvcTagsProvider extends DefaultServerRequestObservationConvention {
              
                  @Override
                  public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                      KeyValues values = super.getHighCardinalityKeyValues(context);
                      values.and(KeyValue.of("a", "b"));
                      return values;
                  }
              }
              """
          )
        );
    }


}
