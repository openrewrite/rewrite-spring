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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class InlineCommentSpringPropertiesTest implements RewriteTest {

    @DocumentExample
    @Test
    void shouldInsertInlineCommentsIntoProperties() {
        rewriteRun(
          spec -> spec.recipe(new InlineCommentSpringProperties(List.of("test.propertyKey1", "test.propertyKey2"), "my comment")),
          yaml("""
              test.propertyKey1: xxx
              test.propertyKey2: yyy""",
            """
              test.propertyKey1: xxx # my comment
              test.propertyKey2: yyy # my comment""",
            spec -> spec.path("application.yaml")),
          properties("""
              test.propertyKey1: xxx
              test.propertyKey2: yyy""",
            """
              test.propertyKey1: xxx # my comment
              test.propertyKey2: yyy # my comment""",
            spec -> spec.path("application.properties")));
    }
}
